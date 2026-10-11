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

    /** Statuses an admin may set by hand. Payment states are set only by the payment flow. */
    public boolean isAdminSettable() {
        return this == FOOD_PROCESSING || this == OUT_FOR_DELIVERY || this == DELIVERED;
    }

    /** Parses the label the admin panel sends, or null if unknown. */
    public static OrderStatus fromLabel(String label) {
        for (OrderStatus s : values()) {
            if (s.label.equals(label)) {
                return s;
            }
        }
        return null;
    }
}
