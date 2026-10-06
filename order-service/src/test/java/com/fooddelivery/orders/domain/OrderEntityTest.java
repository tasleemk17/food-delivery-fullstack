package com.fooddelivery.orders.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OrderEntityTest {

    static Address address() {
        return new Address("Asha", "K", "a@b.com", "1 MG Road", "Pune", "MH", "411001", "India", "9999999999");
    }

    static OrderEntity order(String userId) {
        return new OrderEntity(userId, PaymentMethod.COD, OrderStatus.FOOD_PROCESSING, address(), Instant.now());
    }

    @Test
    void rejectsZeroOrNegativeQuantity() {
        OrderEntity order = order("u1");
        assertThatThrownBy(() -> order.addItem("f1", "Salad", new BigDecimal("120"), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> order.addItem("f1", "Salad", new BigDecimal("120"), -2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativePriceAndAmount() {
        OrderEntity order = order("u1");
        assertThatThrownBy(() -> order.addItem("f1", "Salad", new BigDecimal("-1"), 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> order.setAmount(new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void itemsCannotBeChangedFromOutside() {
        OrderEntity order = order("u1");
        assertThatThrownBy(() -> order.getItems().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
