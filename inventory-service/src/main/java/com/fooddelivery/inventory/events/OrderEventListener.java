package com.fooddelivery.inventory.events;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Reads the order-events topic. Kafka only moves on to the next message after this method
 * returns without an exception; see KafkaErrorHandling for what happens when it throws.
 */
@Component
class OrderEventListener {

    private final InventoryEventProcessor processor;

    OrderEventListener(InventoryEventProcessor processor) {
        this.processor = processor;
    }

    @KafkaListener(topics = "${inventory-events.topic:order-events}")
    void onMessage(String payload) {
        processor.process(payload);
    }
}
