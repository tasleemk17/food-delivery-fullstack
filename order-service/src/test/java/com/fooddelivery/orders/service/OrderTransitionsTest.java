package com.fooddelivery.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.fooddelivery.orders.TestcontainersConfiguration;
import com.fooddelivery.orders.domain.Address;
import com.fooddelivery.orders.domain.OrderEntity;
import com.fooddelivery.orders.domain.OrderRepository;
import com.fooddelivery.orders.domain.OrderStatus;
import com.fooddelivery.orders.domain.PaymentMethod;
import com.fooddelivery.orders.events.OrderEventFactory;
import com.fooddelivery.orders.events.OutboxEvent;
import com.fooddelivery.orders.events.OutboxRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Runs against a real PostgreSQL: the point is that the order and its event are saved
 * together or not at all, and that each real change makes exactly one event.
 */
@SpringBootTest(properties = "outbox.publisher.enabled=false")
@Import(TestcontainersConfiguration.class)
class OrderTransitionsTest {

    private static final Address ADDRESS =
            new Address("Asha", "K", "asha@example.com", "1 MG Road", "Pune", "MH", "411001", "India", "9999999999");

    @Autowired
    OrderTransitions transitions;
    @Autowired
    OrderRepository orders;
    @Autowired
    OutboxRepository outbox;
    @MockitoSpyBean
    OrderEventFactory eventFactory;

    @BeforeEach
    void cleanDatabase() {
        outbox.deleteAll();
        orders.deleteAll();
    }

    private static OrderEntity order(PaymentMethod method, OrderStatus status) {
        OrderEntity o = new OrderEntity("user-1", method, status, ADDRESS, Instant.parse("2026-10-27T10:00:00Z"));
        o.addItem("f1", "Salad", new BigDecimal("120.00"), 2);
        o.setAmount(new BigDecimal("290.00"));
        return o;
    }

    private List<String> eventTypes(UUID orderId) {
        return outbox.findByAggregateIdOrderBySeq(orderId).stream().map(OutboxEvent::getType).toList();
    }

    @Test
    void placingAnOrderSavesItAndItsEventTogether() {
        OrderEntity order = order(PaymentMethod.COD, OrderStatus.FOOD_PROCESSING);

        transitions.place(order);

        assertThat(orders.findById(order.getId())).isPresent();
        List<OutboxEvent> events = outbox.findByAggregateIdOrderBySeq(order.getId());
        assertThat(events).singleElement().satisfies(e -> {
            assertThat(e.getType()).isEqualTo("ORDER_PLACED");
            assertThat(e.getPublishedAt()).isNull();
            assertThat(e.getPayload())
                    .contains(order.getId().toString(), "asha@example.com", "Salad", "COD", "Food Processing");
        });
    }

    @Test
    void amountsInTheEventAlwaysHaveTwoDecimals() {
        // 290 and 120 are whole numbers: a just-created order holds them without decimals
        OrderEntity order = order(PaymentMethod.COD, OrderStatus.FOOD_PROCESSING);
        order.setAmount(new BigDecimal("290"));

        transitions.place(order);

        String payload = outbox.findByAggregateIdOrderBySeq(order.getId()).get(0).getPayload();
        assertThat(payload).contains("\"amount\":290.00", "\"unitPrice\":120.00");
    }

    @Test
    void theEventLeavesOutTheAddressAndThePhoneNumber() {
        OrderEntity order = order(PaymentMethod.COD, OrderStatus.FOOD_PROCESSING);

        transitions.place(order);

        String payload = outbox.findByAggregateIdOrderBySeq(order.getId()).get(0).getPayload();
        assertThat(payload).doesNotContain("9999999999", "1 MG Road", "411001");
    }

    @Test
    void ifTheEventCannotBeWrittenTheOrderIsNotSavedEither() {
        doThrow(new IllegalStateException("cannot write event")).when(eventFactory).create(any(), any());
        OrderEntity order = order(PaymentMethod.COD, OrderStatus.FOOD_PROCESSING);

        assertThatThrownBy(() -> transitions.place(order)).isInstanceOf(IllegalStateException.class);

        // One transaction: the order was rolled back together with the event
        assertThat(orders.count()).isZero();
        assertThat(outbox.count()).isZero();
    }

    @Test
    void payingAnOrderMakesExactlyOnePaidEvent() {
        OrderEntity order = order(PaymentMethod.STRIPE, OrderStatus.PAYMENT_PENDING);
        transitions.place(order);

        assertThat(transitions.markPaid(order.getId())).isTrue();
        // Stripe's webhook and the browser can both report the same payment
        assertThat(transitions.markPaid(order.getId())).isFalse();

        assertThat(eventTypes(order.getId())).containsExactly("ORDER_PLACED", "ORDER_PAID");
        String paid = outbox.findByAggregateIdOrderBySeq(order.getId()).get(1).getPayload();
        assertThat(paid).contains("\"paid\":true", "Food Processing");
    }

    @Test
    void aFailedPaymentIsAnnouncedOnlyTheFirstTime() {
        OrderEntity order = order(PaymentMethod.STRIPE, OrderStatus.PAYMENT_PENDING);
        transitions.place(order);

        assertThat(transitions.markFailed(order.getId())).isTrue();
        // The same news again: still "unpaid" (so stock can be given back again) but no second event
        assertThat(transitions.markFailed(order.getId())).isTrue();

        assertThat(eventTypes(order.getId())).containsExactly("ORDER_PLACED", "ORDER_PAYMENT_FAILED");
    }

    @Test
    void aPaidOrderCannotBeMarkedFailed() {
        OrderEntity order = order(PaymentMethod.STRIPE, OrderStatus.PAYMENT_PENDING);
        transitions.place(order);
        transitions.markPaid(order.getId());

        assertThat(transitions.markFailed(order.getId())).isFalse();

        assertThat(eventTypes(order.getId())).containsExactly("ORDER_PLACED", "ORDER_PAID");
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.FOOD_PROCESSING);
    }

    @Test
    void anAdminMovingAnOrderAlongMakesOneEventPerRealChange() {
        OrderEntity order = order(PaymentMethod.COD, OrderStatus.FOOD_PROCESSING);
        transitions.place(order);

        assertThat(transitions.changeStatus(order.getId(), OrderStatus.OUT_FOR_DELIVERY)).isTrue();
        // Picking the same status again changes nothing, so it says nothing
        assertThat(transitions.changeStatus(order.getId(), OrderStatus.OUT_FOR_DELIVERY)).isTrue();
        assertThat(transitions.changeStatus(order.getId(), OrderStatus.DELIVERED)).isTrue();

        assertThat(eventTypes(order.getId()))
                .containsExactly("ORDER_PLACED", "ORDER_STATUS_CHANGED", "ORDER_STATUS_CHANGED");
        List<OutboxEvent> events = outbox.findByAggregateIdOrderBySeq(order.getId());
        assertThat(events.get(1).getPayload()).contains("Out for delivery");
        assertThat(events.get(2).getPayload()).contains("Delivered");
    }

    @Test
    void anUnpaidStripeOrderCannotBeMovedAlongAndSaysNothing() {
        OrderEntity order = order(PaymentMethod.STRIPE, OrderStatus.PAYMENT_PENDING);
        transitions.place(order);

        assertThat(transitions.changeStatus(order.getId(), OrderStatus.DELIVERED)).isFalse();
        assertThat(transitions.changeStatus(UUID.randomUUID(), OrderStatus.DELIVERED)).isFalse();

        assertThat(eventTypes(order.getId())).containsExactly("ORDER_PLACED");
    }
}
