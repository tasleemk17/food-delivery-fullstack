package com.fooddelivery.inventory.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/**
 * The part of an order event that this service cares about. order-service writes more fields
 * (user, email, items, amount ...); they are ignored on purpose, so this service keeps working
 * when order-service adds or changes them.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderEvent(UUID eventId, String type, Order order) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Order(UUID orderId, String paymentMethod) {}
}
