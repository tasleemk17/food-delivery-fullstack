package com.fooddelivery.orders.domain;

public enum PaymentMethod {
    COD,
    STRIPE;

    /** "cod" / "stripe", the values the React apps use. */
    public String apiValue() {
        return name().toLowerCase();
    }
}
