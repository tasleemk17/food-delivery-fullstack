package com.fooddelivery.orders.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentRulesTest {

    private static final UUID ID = UUID.randomUUID();

    @Test
    void paiseRoundsHalfUp() {
        assertThat(PaymentRules.toPaise(new BigDecimal("120"))).isEqualTo(12000);
        assertThat(PaymentRules.toPaise(new BigDecimal("10.005"))).isEqualTo(1001);
        assertThat(PaymentRules.toPaise(new BigDecimal("0.1"))).isEqualTo(10);
    }

    @Test
    void totalIsLineByLineSumPlusDelivery() {
        var lines = List.of(new PaymentGateway.Line("a", new BigDecimal("120"), 2),
                new PaymentGateway.Line("b", new BigDecimal("90.5"), 1));
        assertThat(PaymentRules.orderTotalPaise(lines, new BigDecimal("50"))).isEqualTo(38050);
    }

    private static StripeSessionInfo session(String status, Long amount, String currency, String orderId) {
        return new StripeSessionInfo("cs_1", status, amount, currency, orderId);
    }

    @Test
    void acceptsOnlyAPaidSessionForThisOrderAndAmount() {
        assertThat(PaymentRules.sessionPaysForOrder(session("paid", 5000L, "inr", ID.toString()), ID, 5000)).isTrue();
        assertThat(PaymentRules.sessionPaysForOrder(session("paid", 5000L, "INR", ID.toString()), ID, 5000)).isTrue();
        assertThat(PaymentRules.sessionPaysForOrder(session("unpaid", 5000L, "inr", ID.toString()), ID, 5000)).isFalse();
        assertThat(PaymentRules.sessionPaysForOrder(session("paid", 4999L, "inr", ID.toString()), ID, 5000)).isFalse();
        assertThat(PaymentRules.sessionPaysForOrder(session("paid", 5000L, "usd", ID.toString()), ID, 5000)).isFalse();
        assertThat(PaymentRules.sessionPaysForOrder(session("paid", 5000L, "inr", UUID.randomUUID().toString()), ID, 5000)).isFalse();
        assertThat(PaymentRules.sessionPaysForOrder(session("paid", null, "inr", ID.toString()), ID, 5000)).isFalse();
        assertThat(PaymentRules.sessionPaysForOrder(null, ID, 5000)).isFalse();
    }
}
