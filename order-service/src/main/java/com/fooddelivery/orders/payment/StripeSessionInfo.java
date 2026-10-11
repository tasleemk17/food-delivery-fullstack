package com.fooddelivery.orders.payment;

/** The few fields of a Stripe Checkout Session this service cares about. */
public record StripeSessionInfo(String id, String paymentStatus, Long amountTotal,
                                String currency, String orderId) {}
