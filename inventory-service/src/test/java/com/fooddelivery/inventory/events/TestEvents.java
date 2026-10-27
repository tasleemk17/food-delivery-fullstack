package com.fooddelivery.inventory.events;

import java.util.UUID;

/** Order events as order-service writes them (only the fields this service reads, plus some it ignores). */
final class TestEvents {

    private TestEvents() {
    }

    static String json(String type, String paymentMethod, UUID orderId) {
        return """
                {"eventId":"%s","type":"%s","occurredAt":"2026-10-27T13:28:18.991Z","order":{
                "orderId":"%s","userId":"6acbd061ed34e19fab2106cd","email":"asha@example.com",
                "paymentMethod":"%s","status":"FOOD_PROCESSING","paid":true,"amount":1950.00,
                "items":[{"foodId":"A","name":"Salad","quantity":2,"unitPrice":600.00}],
                "somethingNew":"ignored"}}
                """.formatted(UUID.randomUUID(), type, orderId, paymentMethod).replace("\n", "");
    }

    static String paid(UUID orderId) {
        return json("ORDER_PAID", "STRIPE", orderId);
    }

    static String codPlaced(UUID orderId) {
        return json("ORDER_PLACED", "COD", orderId);
    }

    static String stripePlaced(UUID orderId) {
        return json("ORDER_PLACED", "STRIPE", orderId);
    }

    static String failed(UUID orderId) {
        return json("ORDER_PAYMENT_FAILED", "STRIPE", orderId);
    }
}
