package com.fooddelivery.orders.domain;

/** Order life cycle. The label is the text the React apps show and send. */
public enum OrderStatus {
    PAYMENT_PENDING("Payment Pending"),
    PAYMENT_FAILED("Payment Failed"),
    FOOD_PROCESSING("Food Processing"),
    OUT_FOR_DELIVERY("Out for delivery"),
    DELIVERED("Delivered");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
