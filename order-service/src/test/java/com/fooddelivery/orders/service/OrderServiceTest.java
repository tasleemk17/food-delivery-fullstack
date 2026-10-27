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
import static org.mockito.Mockito.times;
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
    @Mock OrderTransitions transitions;

    private OrderService service;
    private static final Address ADDRESS =
            new Address("Asha", "K", "a@b.com", "1 MG Road", "Pune", "MH", "411001", "India", "9999999999");

    // 2 x 120 + delivery 50 = 290 rupees = 29000 paise
    private static final CartSnapshot CART = new CartSnapshot(
            Map.of("f1", BigDecimal.valueOf(2)),
            List.of(new CartSnapshot.Food("f1", "Salad", new BigDecimal("120"))));

    @BeforeEach
    void setUp() {
        service = new OrderService(orders, cart, new PricingService(), payments, inventory, transitions,
                Clock.fixed(Instant.parse("2026-10-06T10:00:00Z"), ZoneOffset.UTC));
        given(cart.fetchCart("u1")).willReturn(CART);
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
        verify(transitions).place(saved.capture());
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo("290");
        assertThat(saved.getValue().getStatus()).isEqualTo(OrderStatus.FOOD_PROCESSING);
        assertThat(saved.getValue().getPaymentMethod()).isEqualTo(PaymentMethod.COD);
        verify(cart).clearCart("u1");
    }

    @Test
    void codStillSucceedsIfClearingTheCartFails() {
        doThrow(new UpstreamUnavailableException("down", null)).when(cart).clearCart("u1");
        service.placeCod("u1", ADDRESS);
        verify(transitions).place(any(OrderEntity.class));
    }

    @Test
    void nodeDownMeans503AndNothingSaved() {
        given(cart.fetchCart("u1")).willThrow(new UpstreamUnavailableException("down", null));
        assertThatThrownBy(() -> service.placeCod("u1", ADDRESS)).isInstanceOf(UpstreamUnavailableException.class);
        assertThatThrownBy(() -> service.placeStripe("u1", ADDRESS)).isInstanceOf(UpstreamUnavailableException.class);
        verifyNoInteractions(transitions, payments, inventory);
    }

    @Test
    void deletedFoodSavesNothingAndCleansTheCart() {
        given(cart.fetchCart("u1")).willReturn(new CartSnapshot(Map.of("gone", BigDecimal.ONE), List.of()));
        assertThatThrownBy(() -> service.placeCod("u1", ADDRESS)).isInstanceOf(UnavailableItemException.class);
        verify(cart).removeUnavailable("u1");
        verify(transitions, never()).place(any());
    }

    // -------------------------------------------------------------- Stripe

    @Test
    void stripePlaceSavesPendingOrderAndReturnsSessionUrl() {
        given(payments.createCheckoutSession(any(), any(), any()))
                .willReturn(new PaymentGateway.CheckoutSession("cs_1", "https://stripe/pay"));

        String url = service.placeStripe("u1", ADDRESS);

        assertThat(url).isEqualTo("https://stripe/pay");
        ArgumentCaptor<OrderEntity> saved = ArgumentCaptor.forClass(OrderEntity.class);
        verify(transitions).place(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        verify(orders).setStripeSessionId(any(), eq("cs_1"));
        verify(cart, never()).clearCart(anyString()); // cart is cleared only once paid
    }

    @Test
    void stripeFailureMarksTheOrderFailedAndPropagates() {
        given(payments.createCheckoutSession(any(), any(), any()))
                .willThrow(new PaymentProviderException("boom", null));
        assertThatThrownBy(() -> service.placeStripe("u1", ADDRESS)).isInstanceOf(PaymentProviderException.class);
        verify(transitions).markFailed(any());
    }

    // -------------------------------------------------------------- verify

    @Test
    void verifyMarksPaidAndClearsCartOnlyForTheCallThatWins() {
        UUID id = UUID.randomUUID();
        given(orders.findWithItemsById(id)).willReturn(Optional.of(storedOrder(id, true, "cs_1")));
        given(payments.retrieveSession("cs_1"))
                .willReturn(new StripeSessionInfo("cs_1", "paid", 29000L, "inr", id.toString()));

        given(transitions.markPaid(id)).willReturn(true);
        assertThat(service.verify("u1", id)).isTrue();
        verify(cart).clearCart("u1");

        // second arrival (webhook raced us): update affects 0 rows -> still "Paid", cart not cleared again
        given(transitions.markPaid(id)).willReturn(false);
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
        verify(transitions, never()).markPaid(any());
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
        given(transitions.markPaid(id)).willReturn(true);

        service.handleWebhook("body", "sig");

        verify(transitions).markPaid(id);
        verify(cart).clearCart("u1");
    }

    @Test
    void webhookWithMismatchedAmountDoesNotMarkPaid() {
        UUID id = UUID.randomUUID();
        StripeSessionInfo s = new StripeSessionInfo("cs_1", "paid", 1L, "inr", id.toString());
        given(payments.parseWebhook("body", "sig")).willReturn(new WebhookEvent("evt", "checkout.session.completed", s));
        given(orders.findWithItemsById(id)).willReturn(Optional.of(storedOrder(id, true, "cs_1")));
        service.handleWebhook("body", "sig");
        verify(transitions, never()).markPaid(any());
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
        verify(transitions).markFailed(id);
        service.handleWebhook("b2", "sig");
        service.handleWebhook("b3", "sig");
        verify(transitions, never()).markPaid(any());
    }

    // --------------------------------------------------------------- stock

    private static final List<InventoryPort.StockLine> SALAD_X2 = List.of(new InventoryPort.StockLine("f1", 2));

    @Test
    void codHoldsStockBeforeSavingTheOrder() {
        service.placeCod("u1", ADDRESS);

        ArgumentCaptor<UUID> reservedFor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<OrderEntity> saved = ArgumentCaptor.forClass(OrderEntity.class);
        InOrder order = inOrder(inventory, transitions);
        order.verify(inventory).reserve(reservedFor.capture(), eq(SALAD_X2));
        order.verify(transitions).place(saved.capture());
        // The reservation is keyed by the id the order is saved with
        assertThat(reservedFor.getValue()).isEqualTo(saved.getValue().getId());
        // Confirming the stock is not done here: the ORDER_PLACED event makes inventory-service do it
        verify(inventory, never()).release(any());
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

        verify(transitions, never()).place(any());
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

        verify(transitions, never()).place(any());
        verifyNoInteractions(payments);
    }

    @Test
    void ifSavingTheOrderFailsTheStockIsGivenBack() {
        doThrow(new IllegalStateException("db down")).when(transitions).place(any(OrderEntity.class));

        assertThatThrownBy(() -> service.placeCod("u1", ADDRESS)).isInstanceOf(IllegalStateException.class);

        // No order was saved, so there is no event: this is the one release done directly
        verify(inventory).release(any(UUID.class));
    }

    @Test
    void ifGivingTheStockBackAlsoFailsTheOriginalErrorIsStillTheOneReported() {
        doThrow(new IllegalStateException("db down")).when(transitions).place(any(OrderEntity.class));
        doThrow(new UpstreamUnavailableException("inventory down", null)).when(inventory).release(any());

        assertThatThrownBy(() -> service.placeCod("u1", ADDRESS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db down");
    }

    @Test
    void stripeHoldsStockAndLeavesItToTheEventsToConfirmOrReleaseIt() {
        given(payments.createCheckoutSession(any(), any(), any()))
                .willReturn(new PaymentGateway.CheckoutSession("cs_1", "https://stripe/pay"));

        service.placeStripe("u1", ADDRESS);

        verify(inventory).reserve(any(UUID.class), eq(SALAD_X2));
        verify(inventory, never()).release(any());
    }

    @Test
    void ifStripeFailsTheOrderIsMarkedFailedAndItsEventGivesTheStockBack() {
        given(payments.createCheckoutSession(any(), any(), any()))
                .willThrow(new PaymentProviderException("boom", null));

        assertThatThrownBy(() -> service.placeStripe("u1", ADDRESS)).isInstanceOf(PaymentProviderException.class);

        // Marking it failed makes the ORDER_PAYMENT_FAILED event; inventory-service reads it and releases the stock
        verify(transitions).markFailed(any(UUID.class));
        verify(inventory, never()).release(any());
    }

    @Test
    void payingClearsTheCartOnlyForTheCallThatWinsAndNeverTouchesTheStockDirectly() {
        UUID id = UUID.randomUUID();
        given(orders.findWithItemsById(id)).willReturn(Optional.of(storedOrder(id, true, "cs_1")));
        given(payments.retrieveSession("cs_1"))
                .willReturn(new StripeSessionInfo("cs_1", "paid", 29000L, "inr", id.toString()));

        given(transitions.markPaid(id)).willReturn(true);
        service.verify("u1", id);
        given(transitions.markPaid(id)).willReturn(false);
        service.verify("u1", id);

        verify(cart, times(1)).clearCart("u1");
        // The ORDER_PAID event (made inside markPaid) tells inventory-service to commit
        verifyNoInteractions(inventory);
    }

    @Test
    void anExpiredSessionMarksTheOrderFailedAndLeavesTheStockToItsEvent() {
        UUID id = UUID.randomUUID();
        StripeSessionInfo s = new StripeSessionInfo("cs_1", "unpaid", 29000L, "inr", id.toString());
        given(payments.parseWebhook("b1", "sig")).willReturn(new WebhookEvent("e1", "checkout.session.expired", s));

        service.handleWebhook("b1", "sig");

        verify(transitions).markFailed(id);
        verifyNoInteractions(inventory);
    }

    // -------------------------------------------------------------- status

    @Test
    void statusRules() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> service.updateStatus(id, "Payment Failed")).hasMessage("Invalid status");
        assertThatThrownBy(() -> service.updateStatus(id, "bogus")).hasMessage("Invalid status");

        given(transitions.changeStatus(id, OrderStatus.DELIVERED)).willReturn(false);
        assertThatThrownBy(() -> service.updateStatus(id, "Delivered"))
                .hasMessage("Order not found or not paid yet");

        given(transitions.changeStatus(id, OrderStatus.OUT_FOR_DELIVERY)).willReturn(true);
        service.updateStatus(id, "Out for delivery");
    }
}
