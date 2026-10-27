package com.fooddelivery.orders.events;

/** The kinds of order event that other services can listen for. */
public enum OrderEventType {
    /** An order was saved. For Stripe it is not paid yet; cash on delivery is confirmed straight away. */
    ORDER_PLACED,
    /** Stripe confirmed the payment. */
    ORDER_PAID,
    /** The payment failed or the Stripe session expired. */
    ORDER_PAYMENT_FAILED,
    /** An admin moved the order along (for example to "Out for delivery"). */
    ORDER_STATUS_CHANGED
}
