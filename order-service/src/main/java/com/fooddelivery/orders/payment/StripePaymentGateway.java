package com.fooddelivery.orders.payment;

import com.fooddelivery.orders.config.AppProperties;
import com.fooddelivery.orders.config.StripeProperties;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class StripePaymentGateway implements PaymentGateway {

    private final StripeProperties stripe;
    private final AppProperties app;

    public StripePaymentGateway(StripeProperties stripe, AppProperties app) {
        this.stripe = stripe;
        this.app = app;
    }

    @Override
    public CheckoutSession createCheckoutSession(UUID orderId, List<Line> lines, BigDecimal deliveryCharge) {
        requireKey();
        String base = app.frontendUrl();
        SessionCreateParams.Builder params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(base + "/verify?orderId=" + orderId + "&session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(base + "/verify?orderId=" + orderId)
                .putMetadata("orderId", orderId.toString());

        for (Line l : lines) {
            params.addLineItem(lineItem(l.name(), PaymentRules.toPaise(l.unitPrice()), l.quantity()));
        }
        params.addLineItem(lineItem("Delivery Charges", PaymentRules.toPaise(deliveryCharge), 1));

        // Same idempotency key for the same order: a retry can never create a second session
        RequestOptions options = RequestOptions.builder()
                .setApiKey(stripe.secretKey())
                .setIdempotencyKey("order-" + orderId)
                .build();
        try {
            Session s = Session.create(params.build(), options);
            return new CheckoutSession(s.getId(), s.getUrl());
        } catch (StripeException e) {
            throw new PaymentProviderException("create session failed: " + e.getMessage(), e);
        }
    }

    @Override
    public StripeSessionInfo retrieveSession(String sessionId) {
        requireKey();
        try {
            Session s = Session.retrieve(sessionId, RequestOptions.builder().setApiKey(stripe.secretKey()).build());
            return toInfo(s);
        } catch (StripeException e) {
            throw new PaymentProviderException("retrieve session failed: " + e.getMessage(), e);
        }
    }

    @Override
    public WebhookEvent parseWebhook(String payload, String signatureHeader) {
        if (!stripe.hasWebhookSecret()) {
            throw new InvalidWebhookException(HttpStatus.SERVICE_UNAVAILABLE, "Webhook not configured");
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, stripe.webhookSecret());
        } catch (SignatureVerificationException | RuntimeException e) {
            throw new InvalidWebhookException(HttpStatus.BAD_REQUEST, "Invalid signature");
        }
        StripeSessionInfo session = null;
        StripeObject obj = event.getDataObjectDeserializer().getObject().orElse(null);
        if (obj == null) {
            try {
                obj = event.getDataObjectDeserializer().deserializeUnsafe();
            } catch (Exception e) {
                obj = null;
            }
        }
        if (obj instanceof Session s) {
            session = toInfo(s);
        }
        return new WebhookEvent(event.getId(), event.getType(), session);
    }

    private static StripeSessionInfo toInfo(Session s) {
        String orderId = s.getMetadata() == null ? null : s.getMetadata().get("orderId");
        return new StripeSessionInfo(s.getId(), s.getPaymentStatus(), s.getAmountTotal(), s.getCurrency(), orderId);
    }

    private static SessionCreateParams.LineItem lineItem(String name, long unitPaise, long qty) {
        return SessionCreateParams.LineItem.builder()
                .setQuantity(qty)
                .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                        .setCurrency(PaymentRules.CURRENCY)
                        .setUnitAmount(unitPaise)
                        .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                .setName(name).build())
                        .build())
                .build();
    }

    private void requireKey() {
        if (!stripe.hasSecretKey()) {
            throw new PaymentProviderException("STRIPE_SECRET_KEY is not set", null);
        }
    }
}
