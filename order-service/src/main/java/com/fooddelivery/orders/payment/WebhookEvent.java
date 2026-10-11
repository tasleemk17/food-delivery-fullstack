package com.fooddelivery.orders.payment;

/** A signature-verified Stripe event, reduced to what the webhook handler needs. */
public record WebhookEvent(String id, String type, StripeSessionInfo session) {}
