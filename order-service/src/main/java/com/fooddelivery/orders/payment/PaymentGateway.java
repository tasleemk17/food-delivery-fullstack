package com.fooddelivery.orders.payment;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Everything order-service needs from the payment provider (Stripe). */
public interface PaymentGateway {

    record Line(String name, BigDecimal unitPrice, int quantity) {}

    /** Creates a Checkout Session and returns its id and redirect URL. */
    CheckoutSession createCheckoutSession(UUID orderId, List<Line> lines, BigDecimal deliveryCharge);

    record CheckoutSession(String id, String url) {}

    StripeSessionInfo retrieveSession(String sessionId);

    /** Verifies the signature and parses the event; throws if it is not genuine. */
    WebhookEvent parseWebhook(String payload, String signatureHeader);
}
