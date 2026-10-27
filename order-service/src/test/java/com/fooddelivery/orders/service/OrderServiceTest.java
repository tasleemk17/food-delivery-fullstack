package com.fooddelivery.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fooddelivery.orders.domain.Address;
import com.fooddelivery.orders.domain.OrderEntity;
import com.fooddelivery.orders.domain.OrderRepository;
import com.fooddelivery.orders.domain.OrderStatus;
import com.fooddelivery.orders.domain.PaymentMethod;
import com.fooddelivery.orders.inventory.InventoryPort;
import com.fooddelivery.orders.node.CartPort;
import com.fooddelivery.orders.node.CartSnapshot;
import com.fooddelivery.orders.payment.PaymentGateway;
import com.fooddelivery.orders.payment.PaymentProviderException;
import com.fooddelivery.orders.payment.StripeSessionInfo;
import com.fooddelivery.orders.payment.WebhookEvent;
import com.fooddelivery.orders.pricing.PricingService;
import com.fooddelivery.orders.pricing.UnavailableItemException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceTest {

    @Mock OrderRepository orders;
    @Mock CartPort cart;
    @Mock PaymentGateway payments;
    @Mock InventoryPort inventory;

    private OrderService service;
    private static final Address ADDRESS =
            new Address("Asha", "K", "a@b.com", "1 MG Road", "Pune", "MH", "411001", "India", "9999999999");

    // 2 x 120 + delivery 50 = 290 rupees = 29000 paise
    private static final CartSnapshot CART = new CartSnapshot(
            Map.of("f1", BigDecimal.valueOf(2)),
            List.of(new CartSnapshot.Food("f1", "Salad", new BigDecimal("120"))));

    @BeforeEach
    void setUp() {
        service = new OrderService(orders, cart, new PricingService(), payments, inventory,
                Clock.fixed(Instant.parse("2026-10-06T10:00:00Z"), ZoneOffset.UTC));
        given(cart.fetchCart("u1")).willReturn(CART);
        given(orders.save(any(OrderEntity.class))).willAnswer(i -> i.getArgument(0));
    }

    private static OrderEntity storedOrder(UUID id, boolean stripe, String sessionId) {
        OrderEntity o = new OrderEntity("u1", stripe ? PaymentMethod.STRIPE : PaymentMethod.COD,
                OrderStatus.PAYMENT_PENDING, ADDRESS, Instant.now());
        o.addItem("f1", "Salad", new BigDecimal("120"), 2);
        o.setAmount(new BigDecimal("290"));
        try {
            var f = OrderEntity.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(o, id);
            var s = OrderEntity.class.getDeclaredField("stripeSessionId");
            s.setAccessible(true);
            s.set(o, sessionId);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return o;
    }

    // ----------------------------------------------------------------- COD

    @Test
    void codSavesServerPricedOrderAndClearsCart() {
        service.placeCod("u1", ADDRESS);

        ArgumentCaptor<OrderEntity> saved = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orders).save(saved.capture());
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo("290");
        assertThat(saved.getValue().getStatus()).isEqualTo(OrderStatus.FOOD_PROCESSING);
        assertThat(saved.getValue().getPaymentMethod()).isEqualTo(PaymentMethod.COD);
        verify(cart).clearCart("u1");
    }

    @Test
    void codStillSucceedsIfClearingTheCartFails() {
        doThrow(new UpstreamUnavailableException("down", null)).when(cart).clearCart("u1");
        service.placeCod("u1", ADDRESS);
        verify(orders).save(any(OrderEntity.class));
    }

    @Test
    void nodeDownMeans503AndNothingSaved() {
        given(cart.fetchCart("u1")).willThrow(new UpstreamUnavailableException("down", null));
        assertThatThrownBy(() -> service.placeCod("u1", ADDRESS)).isInstanceOf(UpstreamUnavailableException.class);
        assertThatThrownBy(() -> service.placeStripe("u1", ADDRESS)).isInstanceOf(UpstreamUnavailableException.class);
        verifyNoInteractions(orders, payments, inventory);
    }

    @Test
    void deletedFoodSavesNothingAndCleansTheCart() {
        given(cart.fetchCart("u1")).willReturn(new CartSnapshot(Map.of("gone", BigDecimal.ONE), List.of()));
        assertThatThrownBy(() -> service.placeCod("u1", ADDRESS)).isInstanceOf(UnavailableItemException.class);
        verify(cart).removeUnavailable("u1");
        verify(orders, never()).save(any());
    }

    // -------------------------------------------------------------- Stripe

    @Test
    void stripePlaceSavesPendingOrderAndReturnsSessionUrl() {
        given(payments.createCheckoutSession(any(), any(), any()))
                .willReturn(new PaymentGateway.CheckoutSession("cs_1", "https://stripe/pay"));

        String url = service.placeStripe("u1", ADDRESS);

        assertThat(url).isEqualTo("https://stripe/pay");
        ArgumentCaptor<OrderEntity> saved = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orders).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        verify(orders).setStripeSessionId(any(), eq("cs_1"));
        verify(cart, never()).clearCart(anyString()); // cart is cleared only once paid
    }

    @Test
    void stripeFailureMarksTheOrderFailedAndPropagates() {
        given(payments.createCheckoutSession(any(), any(), any()))
                .willThrow(new PaymentProviderException("boom", null));
        assertThatThrownBy(() -> service.placeStripe("u1", ADDRESS)).isInstanceOf(PaymentProviderException.class);
        verify(orders).markFailedIfUnpaid(any());
    }

    // -------------------------------------------------------------- verify

    @Test
    void verifyMarksPaidAndClearsCartOnlyForTheCallThatWins() {
        UUID id = UUID.randomUUID();
        given(orders.findWithItemsById(id)).willReturn(Optional.of(storedOrder(id, true, "cs_1")));
        given(payments.retrieveSession("cs_1"))
                .willReturn(new StripeSessionInfo("cs_1", "paid", 29000L, "inr", id.toString()));

        given(orders.markPaidIfUnpaid(id)).willReturn(1);
        assertThat(service.verify("u1", id)).isTrue();
        verify(cart).clearCart("u1");

        // second arrival (webhook raced us): update affects 0 rows -> still "Paid", cart not cleared again
        given(orders.markPaidIfUnpaid(id)).willReturn(0);
        assertThat(service.verify("u1", id)).isTrue();
        verify(cart).clearCart("u1"); // still exactly once
    }

    @Test
    void verifyRejectsWrongAmount() {
        UUID id = UUID.randomUUID();
        given(orders.findWithItemsById(id)).willReturn(Optional.of(storedOrder(id, true, "cs_1")));
        given(payments.retrieveSession("cs_1"))
                .willReturn(new StripeSessionInfo("cs_1", "paid", 100L, "inr", id.toString()));
        assertThat(service.verify("u1", id)).isFalse();
        verify(orders, never()).markPaidIfUnpaid(any());
    }

    @Test
    void verifyOfSomeoneElsesOrderIs404() {
        UUID id = UUID.randomUUID();
        given(orders.findWithItemsById(id)).willReturn(Optional.of(storedOrder(id, true, "cs_1")));
        assertThatThrownBy(() -> service.verify("other-user", id))
                .isInstanceOf(OrderException.class).hasMessage("Order not found");
        verifyNoInteractions(payments);
    }

    @Test
    void verifyWithoutASessionIsNotPaid() {
        UUID id = UUID.randomUUID();
        given(orders.findWithItemsById(id)).willReturn(Optional.of(storedOrder(id, true, null)));
        assertThat(service.verify("u1", id)).isFalse();
        verifyNoInteractions(payments);
    }

    // ------------------------------------------------------------- webhook

    @Test
    void webhookCompletedMarksPaid() {
        UUID id = UUID.randomUUID();
        StripeSessionInfo s = new StripeSessionInfo("cs_1", "paid", 29000L, "inr", id.toString());
        given(payments.parseWebhook("body", "sig")).willReturn(new WebhookEvent("evt", "checkout.session.completed", s));
        given(orders.findWithItemsById(id)).willReturn(Optional.of(storedOrder(id, true, "cs_1")));
        given(orders.markPaidIfUnpaid(id)).willReturn(1);

        service.handleWebhook("body", "sig");

        verify(orders).markPaidIfUnpaid(id);
        verify(cart).clearCart("u1");
    }

    @Test
    void webhookWithMismatchedAmountDoesNotMarkPaid() {
        UUID id = UUID.randomUUID();
        StripeSessionInfo s = new StripeSessionInfo("cs_1", "paid", 1L, "inr", id.toString());
        given(payments.parseWebhook("body", "sig")).willReturn(new WebhookEvent("evt", "checkout.session.completed", s));
        given(orders.findWithItemsById(id)).willReturn(Optional.of(storedOrder(id, true, "cs_1")));
        service.handleWebhook("body", "sig");
        verify(orders, never()).markPaidIfUnpaid(any());
    }

    @Test
    void webhookExpiredMarksFailedAndIgnoresUnknownEvents() {
        UUID id = UUID.randomUUID();
        StripeSessionInfo s = new StripeSessionInfo("cs_1", "unpaid", 29000L, "inr", id.toString());
        given(payments.parseWebhook("b1", "sig")).willReturn(new WebhookEvent("e1", "checkout.session.expired", s));
        given(payments.parseWebhook("b2", "sig")).willReturn(new WebhookEvent("e2", "charge.refunded", s));
        given(payments.parseWebhook("b3", "sig")).willReturn(new WebhookEvent("e3", "checkout.session.completed",
                new StripeSessionInfo("cs", "paid", 1L, "inr", "not-a-uuid")));

        service.handleWebhook("b1", "sig");
        verify(orders).markFailedIfUnpaid(id);
        service.handleWebhook("b2", "sig");
        service.handleWebhook("b3", "sig");
        verify(orders, never()).markPaidIfUnpaid(any());
    }

    // --------------------------------------------------------------- stock

    private static final List<InventoryPort.StockLine> SALAD_X2 = List.of(new InventoryPort.StockLine("f1", 2));

    @Test
    void codHoldsStockBeforeSavingThenConfirmsIt() {
        service.placeCod("u1", ADDRESS);

        ArgumentCaptor<UUID> reservedFor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<OrderEntity> saved = ArgumentCaptor.forClass(OrderEntity.class);
        InOrder order = inOrder(inventory, orders);
        order.verify(inventory).reserve(reservedFor.capture(), eq(SALAD_X2));
        order.verify(orders).save(saved.capture());
        order.verify(inventory).commit(saved.getValue().getId());
        // The reservation is keyed by the id the order is saved with
        assertThat(reservedFor.getValue()).isEqualTo(saved.getValue().getId());
    }

    @Test
    void outOfStockSavesNothingAndNamesTheFood() {
        doThrow(new OutOfStockException("f1", 0)).when(inventory).reserve(any(), any());

        assertThatThrownBy(() -> service.placeCod("u1", ADDRESS))
                .isInstanceOfSatisfying(OrderException.class, e -> {
                    assertThat(e.status().value()).isEqualTo(409);
                    assertThat(e.getMessage()).startsWith("Salad is out of stock");
                });
        assertThatThrownBy(() -> service.placeStripe("u1", ADDRESS)).isInstanceOf(OrderException.class);

        verify(orders, never()).save(any());
        verifyNoInteractions(payments);
        verify(cart, never()).clearCart(anyString()); // the customer keeps their cart to fix it
    }

    @Test
    void whenOnlySomeAreLeftTheMessageSaysHowMany() {
        doThrow(new OutOfStockException("f1", 1)).when(inventory).reserve(any(), any());

        assertThatThrownBy(() -> service.placeStripe("u1", ADDRESS))
                .hasMessageStartingWith("Only 1 left of Salad");
    }

    @Test
    void inventoryDownMeans503AndNothingIsSaved() {
        doThrow(new UpstreamUnavailableException("down", null)).when(inventory).reserve(any(), any());

        assertThatThrownBy(() -> service.placeCod("u1", ADDRESS)).isInstanceOf(UpstreamUnavailableException.class);
        assertThatThrownBy(() -> service.placeStripe("u1", ADDRESS)).isInstanceOf(UpstreamUnavailableException.class);

        verify(orders, never()).save(any());
        verifyNoInteractions(payments);
    }

    @Test
    void ifSavingTheOrderFailsTheStockIsGivenBack() {
        given(orders.save(any(OrderEntity.class))).willThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.placeCod("u1", ADDRESS)).isInstanceOf(IllegalStateException.class);

        verify(inventory).release(any(UUID.class));
        verify(inventory, never()).commit(any());
    }

    @Test
    void codStillSucceedsIfConfirmingTheStockFails() {
        doThrow(new UpstreamUnavailableException("down", null)).when(inventory).commit(any());

        service.placeCod("u1", ADDRESS);

        verify(orders).save(any(OrderEntity.class));
        verify(cart).clearCart("u1");
    }

    @Test
    void stripeHoldsStockButDoesNotConfirmItUntilPaid() {
        given(payments.createCheckoutSession(any(), any(), any()))
                .willReturn(new PaymentGateway.CheckoutSession("cs_1", "https://stripe/pay"));

        service.placeStripe("u1", ADDRESS);

        verify(inventory).reserve(any(UUID.class), eq(SALAD_X2));
        verify(inventory, never()).commit(any());
        verify(inventory, never()).release(any());
    }

    @Test
    void ifStripeFailsTheStockIsGivenBack() {
        given(payments.createCheckoutSession(any(), any(), any()))
                .willThrow(new PaymentProviderException("boom", null));

        assertThatThrownBy(() -> service.placeStripe("u1", ADDRESS)).isInstanceOf(PaymentProviderException.class);

        verify(inventory).release(any(UUID.class));
    }

    @Test
    void payingConfirmsTheStockOnlyForTheCallThatWins() {
        UUID id = UUID.randomUUID();
        given(orders.findWithItemsById(id)).willReturn(Optional.of(storedOrder(id, true, "cs_1")));
        given(payments.retrieveSession("cs_1"))
                .willReturn(new StripeSessionInfo("cs_1", "paid", 29000L, "inr", id.toString()));

        given(orders.markPaidIfUnpaid(id)).willReturn(1);
        service.verify("u1", id);
        given(orders.markPaidIfUnpaid(id)).willReturn(0);
        service.verify("u1", id);

        verify(inventory).commit(id); // exactly once
    }

    @Test
    void anExpiredSessionGivesTheStockBack() {
        UUID id = UUID.randomUUID();
        StripeSessionInfo s = new StripeSessionInfo("cs_1", "unpaid", 29000L, "inr", id.toString());
        given(payments.parseWebhook("b1", "sig")).willReturn(new WebhookEvent("e1", "checkout.session.expired", s));
        given(orders.markFailedIfUnpaid(id)).willReturn(1);

        service.handleWebhook("b1", "sig");

        verify(inventory).release(id);
    }

    @Test
    void anExpiredSessionOfAnOrderThatWasAlreadyPaidKeepsTheStock() {
        UUID id = UUID.randomUUID();
        StripeSessionInfo s = new StripeSessionInfo("cs_1", "unpaid", 29000L, "inr", id.toString());
        given(payments.parseWebhook("b1", "sig")).willReturn(new WebhookEvent("e1", "checkout.session.expired", s));
        given(orders.markFailedIfUnpaid(id)).willReturn(0); // paid already: nothing was marked failed

        service.handleWebhook("b1", "sig");

        verify(inventory, never()).release(any());
    }

    // -------------------------------------------------------------- status

    @Test
    void statusRules() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> service.updateStatus(id, "Payment Failed")).hasMessage("Invalid status");
        assertThatThrownBy(() -> service.updateStatus(id, "bogus")).hasMessage("Invalid status");

        given(orders.updateStatusIfPayable(id, OrderStatus.DELIVERED)).willReturn(0);
        assertThatThrownBy(() -> service.updateStatus(id, "Delivered"))
                .hasMessage("Order not found or not paid yet");

        given(orders.updateStatusIfPayable(id, OrderStatus.OUT_FOR_DELIVERY)).willReturn(1);
        service.updateStatus(id, "Out for delivery");
    }
}
