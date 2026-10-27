package com.fooddelivery.orders.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.fooddelivery.orders.TestcontainersConfiguration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Kafka is unreachable (nothing listens on port 1): the events must wait, not get lost. */
@SpringBootTest(properties = {
        "outbox.publisher.enabled=false",
        "outbox.send-timeout-ms=1500",
        "spring.kafka.bootstrap-servers=localhost:1",
        "spring.kafka.producer.properties.max.block.ms=1000",
        "spring.kafka.producer.properties.delivery.timeout.ms=2000",
        "spring.kafka.producer.properties.request.timeout.ms=1000"
})
@Import(TestcontainersConfiguration.class)
class OutboxPublisherKafkaDownTest {

    @Autowired
    OutboxPublisher publisher;
    @Autowired
    OutboxRepository outbox;

    @BeforeEach
    void cleanOutbox() {
        outbox.deleteAll();
    }

    @Test
    void whenKafkaIsDownTheEventsStayInTheOutboxAndNothingThrows() {
        UUID order = UUID.randomUUID();
        for (String type : new String[] {"ORDER_PLACED", "ORDER_PAID"}) {
            outbox.save(new OutboxEvent(UUID.randomUUID(), order, type, "{}", Instant.now()));
        }

        int sent = publisher.publishPending();

        assertThat(sent).isZero();
        assertThat(outbox.countByPublishedAtIsNull()).isEqualTo(2);
        // The first event was tried and failed; the second was NOT tried, so it cannot overtake the first
        var events = outbox.findByAggregateIdOrderBySeq(order);
        assertThat(events.get(0).getAttempts()).isEqualTo(1);
        assertThat(events.get(1).getAttempts()).isZero();
    }
}
