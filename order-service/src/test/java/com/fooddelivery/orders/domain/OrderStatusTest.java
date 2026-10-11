package com.fooddelivery.orders.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrderStatusTest {

    @Test
    void parsesLabelsTheAdminPanelSends() {
        assertThat(OrderStatus.fromLabel("Out for delivery")).isEqualTo(OrderStatus.OUT_FOR_DELIVERY);
        assertThat(OrderStatus.fromLabel("nonsense")).isNull();
        assertThat(OrderStatus.fromLabel(null)).isNull();
    }

    @Test
    void adminCanOnlySetDeliveryStates() {
        assertThat(OrderStatus.FOOD_PROCESSING.isAdminSettable()).isTrue();
        assertThat(OrderStatus.OUT_FOR_DELIVERY.isAdminSettable()).isTrue();
        assertThat(OrderStatus.DELIVERED.isAdminSettable()).isTrue();
        assertThat(OrderStatus.PAYMENT_PENDING.isAdminSettable()).isFalse();
        assertThat(OrderStatus.PAYMENT_FAILED.isAdminSettable()).isFalse();
    }
}
