package com.fooddelivery.notifications.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One message on the order-events topic, as order-service writes it.
 *
 * ignoreUnknown: order-service may add fields later; this service must keep
 * working and simply ignore what it does not know yet.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderEvent(UUID eventId, String type, Instant occurredAt, Order order) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Order(UUID orderId, String userId, String email, String firstName,
                        String paymentMethod, String status, String statusLabel, boolean paid,
                        BigDecimal amount, List<Item> items, Instant createdAt) {

        public List<Item> items() {
            return items == null ? List.of() : items;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String foodId, String name, int quantity, BigDecimal unitPrice) {}
}
