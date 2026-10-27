package com.fooddelivery.orders.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.orders.domain.OrderEntity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Turns an order into the JSON event that goes to Kafka.
 *
 * The event carries everything a listener needs (who, what, how much), so a
 * listener never has to call back into order-service. It deliberately leaves
 * out the street address and the phone number: nobody listening needs them.
 */
@Component
public class OrderEventFactory {

    /** The message body. eventId lets a listener spot a message it has already handled. */
    public record Envelope(UUID eventId, String type, Instant occurredAt, OrderData order) {}

    public record OrderData(UUID orderId, String userId, String email, String firstName,
                            String paymentMethod, String status, String statusLabel, boolean paid,
                            BigDecimal amount, List<Item> items, Instant createdAt) {}

    public record Item(String foodId, String name, int quantity, BigDecimal unitPrice) {}

    private final ObjectMapper mapper;
    private final Clock clock;

    public OrderEventFactory(ObjectMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Always two decimals. An order that was just created still holds 1350 in memory, while one read
     * back from the database gives 1350.00, so without this the same amount was written two ways.
     */
    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /** The order must have its items loaded. */
    public OutboxEvent create(OrderEventType type, OrderEntity order) {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now(clock);

        List<Item> items = order.getItems().stream()
                .map(i -> new Item(i.getFoodId(), i.getName(), i.getQuantity(), money(i.getUnitPrice())))
                .toList();
        OrderData data = new OrderData(order.getId(), order.getUserId(), order.getAddress().getEmail(),
                order.getAddress().getFirstName(), order.getPaymentMethod().name(), order.getStatus().name(),
                order.getStatus().label(), order.isPaid(), money(order.getAmount()), items, order.getCreatedAt());

        try {
            String json = mapper.writeValueAsString(new Envelope(eventId, type.name(), now, data));
            return new OutboxEvent(eventId, order.getId(), type.name(), json, now);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not write the " + type + " event of order " + order.getId(), e);
        }
    }
}
