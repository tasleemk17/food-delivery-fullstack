package com.fooddelivery.notifications;

import java.util.UUID;

/** Order events as order-service writes them (the same JSON shape), for the tests. */
public final class TestEvents {

    public static final String ORDER_ID = "0b932fbe-67a4-4575-9e41-06d21fab4117";

    private TestEvents() {
    }

    /** Two foods (2 x 600 + 2 x 350 = 1900) plus 50 delivery = 1950. Has an extra field the service does not know. */
    public static String json(UUID eventId, String type, String paymentMethod, String status,
                              String statusLabel, boolean paid) {
        return """
                {"eventId":"%s","type":"%s","occurredAt":"2026-10-27T13:28:18.991Z","order":{
                "orderId":"%s","userId":"6acbd061ed34e19fab2106cd","email":"asha@example.com",
                "firstName":"Asha","paymentMethod":"%s","status":"%s","statusLabel":"%s","paid":%s,
                "amount":1950.00,"items":[
                {"foodId":"f7","name":"food 7","quantity":2,"unitPrice":600.00},
                {"foodId":"f8","name":"food 8","quantity":2,"unitPrice":350.00}],
                "createdAt":"2026-10-27T13:28:18.902388Z","somethingNew":"ignored"}}
                """.formatted(eventId, type, ORDER_ID, paymentMethod, status, statusLabel, paid)
                .replace("\n", "");
    }

    public static String codPlaced(UUID eventId) {
        return json(eventId, "ORDER_PLACED", "COD", "FOOD_PROCESSING", "Food Processing", false);
    }

    public static String stripePlaced(UUID eventId) {
        return json(eventId, "ORDER_PLACED", "STRIPE", "PAYMENT_PENDING", "Payment Pending", false);
    }

    public static String stripePaid(UUID eventId) {
        return json(eventId, "ORDER_PAID", "STRIPE", "FOOD_PROCESSING", "Food Processing", true);
    }

    public static String paymentFailed(UUID eventId) {
        return json(eventId, "ORDER_PAYMENT_FAILED", "STRIPE", "PAYMENT_FAILED", "Payment Failed", false);
    }

    public static String delivered(UUID eventId) {
        return json(eventId, "ORDER_STATUS_CHANGED", "STRIPE", "DELIVERED", "Delivered", true);
    }
}
