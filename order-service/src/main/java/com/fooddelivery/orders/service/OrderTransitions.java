package com.fooddelivery.orders.service;

import com.fooddelivery.orders.domain.OrderEntity;
import com.fooddelivery.orders.domain.OrderRepository;
import com.fooddelivery.orders.domain.OrderStatus;
import com.fooddelivery.orders.events.OrderEventFactory;
import com.fooddelivery.orders.events.OrderEventType;
import com.fooddelivery.orders.events.OutboxRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every change to an order that other services should hear about.
 *
 * Each method is ONE database transaction that changes the order AND writes its
 * event to the outbox. Either both are saved or neither is, so an event can
 * never be missing for a change that happened, or exist for one that did not.
 *
 * This is a separate bean (not part of OrderService) on purpose: OrderService
 * stays free of transactions so a slow Stripe or inventory call never holds a
 * database connection, and a transaction only wraps the quick database work.
 */
@Service
public class OrderTransitions {

    private final OrderRepository orders;
    private final OutboxRepository outbox;
    private final OrderEventFactory events;

    public OrderTransitions(OrderRepository orders, OutboxRepository outbox, OrderEventFactory events) {
        this.orders = orders;
        this.outbox = outbox;
        this.events = events;
    }

    /** Saves a new order and announces it. */
    @Transactional
    public void place(OrderEntity order) {
        orders.save(order);
        outbox.save(events.create(OrderEventType.ORDER_PLACED, order));
    }

    /**
     * Marks a Stripe order paid and announces it.
     * Returns true only for the one call that really flipped it, so the event is sent once.
     */
    @Transactional
    public boolean markPaid(UUID orderId) {
        if (orders.markPaidIfUnpaid(orderId) != 1) {
            return false;
        }
        announce(OrderEventType.ORDER_PAID, orderId);
        return true;
    }

    /**
     * Marks an unpaid order's payment as failed.
     * Returns true if the order is unpaid (so the caller can give its stock back, which is safe to repeat).
     * The event is sent only the first time, not when the same news arrives again.
     */
    @Transactional
    public boolean markFailed(UUID orderId) {
        boolean alreadyFailed = orders.findById(orderId)
                .map(o -> o.getStatus() == OrderStatus.PAYMENT_FAILED)
                .orElse(false);
        if (orders.markFailedIfUnpaid(orderId) != 1) {
            return false;
        }
        if (!alreadyFailed) {
            announce(OrderEventType.ORDER_PAYMENT_FAILED, orderId);
        }
        return true;
    }

    /**
     * An admin moves the order along. Returns false if the order does not exist or is an unpaid Stripe order.
     * Choosing the status the order already has changes nothing and sends no event.
     */
    @Transactional
    public boolean changeStatus(UUID orderId, OrderStatus status) {
        boolean sameStatus = orders.findById(orderId).map(o -> o.getStatus() == status).orElse(false);
        if (orders.updateStatusIfPayable(orderId, status) == 0) {
            return false;
        }
        if (!sameStatus) {
            announce(OrderEventType.ORDER_STATUS_CHANGED, orderId);
        }
        return true;
    }

    private void announce(OrderEventType type, UUID orderId) {
        // Read the order again so the event shows what was just saved
        orders.findWithItemsById(orderId)
                .ifPresent(order -> outbox.save(events.create(type, order)));
    }
}
