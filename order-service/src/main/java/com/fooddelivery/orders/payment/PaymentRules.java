package com.fooddelivery.orders.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/** Money rules shared by checkout creation and payment confirmation. */
public final class PaymentRules {

    public static final String CURRENCY = "inr";

    private PaymentRules() {}

    /** Rupees to paise, rounded half-up (Stripe works in the smallest unit). */
    public static long toPaise(BigDecimal rupees) {
        return rupees.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** Sum of item lines plus delivery, in paise, computed line by line like the checkout lines. */
    public static long orderTotalPaise(List<PaymentGateway.Line> lines, BigDecimal deliveryCharge) {
        long total = toPaise(deliveryCharge);
        for (PaymentGateway.Line l : lines) {
            total += toPaise(l.unitPrice()) * l.quantity();
        }
        return total;
    }

    /**
     * True only if the Stripe session really is a paid session for this order and amount.
     * Guards against a paid session of another order or a cheaper one being replayed.
     */
    public static boolean sessionPaysForOrder(StripeSessionInfo s, UUID orderId, long expectedPaise) {
        return s != null
                && "paid".equals(s.paymentStatus())
                && orderId.toString().equals(s.orderId())
                && s.amountTotal() != null && s.amountTotal() == expectedPaise
                && CURRENCY.equalsIgnoreCase(s.currency());
    }
}
