package com.fooddelivery.orders.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.fooddelivery.orders.TestcontainersConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/** A real Kafka broker and a real PostgreSQL in Docker. Docker Desktop must be running. */
@SpringBootTest(properties = "outbox.publisher.enabled=false")
@Import(TestcontainersConfiguration.class)
@Testcontainers
class OutboxPublisherKafkaTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));

    @DynamicPropertySource
    static void kafkaAddress(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    OutboxPublisher publisher;
    @Autowired
    OutboxRepository outbox;

    @BeforeEach
    void cleanOutbox() {
        outbox.deleteAll();
    }

    private OutboxEvent saveEvent(UUID orderId, String type) {
        return outbox.save(new OutboxEvent(UUID.randomUUID(), orderId, type,
                "{\"type\":\"" + type + "\"}", Instant.now()));
    }

    /**
     * Reads the topic from the start and keeps only the messages of one order (the tests share one topic),
     * until it has seen `expected` of them or 15 s passed.
     */
    private List<ConsumerRecord<String, String>> readTopic(UUID order, int expected) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<ConsumerRecord<String, String>> seen = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of("order-events"));
            long deadline = System.currentTimeMillis() + 15_000;
            while (seen.size() < expected && System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(r -> {
                    if (order.toString().equals(r.key())) {
                        seen.add(r);
                    }
                });
            }
        }
        return seen;
    }

    @Test
    void sendsTheEventsMarksThemSentAndSendsEachOnlyOnce() {
        UUID order = UUID.randomUUID();
        saveEvent(order, "ORDER_PLACED");
        saveEvent(order, "ORDER_PAID");
        saveEvent(order, "ORDER_STATUS_CHANGED");

        assertThat(publisher.publishPending()).isEqualTo(3);
        assertThat(outbox.countByPublishedAtIsNull()).isZero();
        // Nothing is left, so a second run sends nothing (no duplicates)
        assertThat(publisher.publishPending()).isZero();

        List<ConsumerRecord<String, String>> messages = readTopic(order, 3);
        assertThat(messages).hasSize(3);
        // Key = order id, and the events of one order arrive in the order they happened
        assertThat(messages).allSatisfy(m -> assertThat(m.key()).isEqualTo(order.toString()));
        assertThat(messages.stream().map(ConsumerRecord::value).toList()).containsExactly(
                "{\"type\":\"ORDER_PLACED\"}", "{\"type\":\"ORDER_PAID\"}", "{\"type\":\"ORDER_STATUS_CHANGED\"}");
        // A header names the type, so a listener can skip events it does not care about
        assertThat(new String(messages.get(1).headers().lastHeader("event-type").value())).isEqualTo("ORDER_PAID");
    }

    @Test
    void eventsOfOneOrderAlwaysLandOnTheSamePartition() {
        UUID order = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            saveEvent(order, "ORDER_STATUS_CHANGED");
        }

        publisher.publishPending();

        List<ConsumerRecord<String, String>> messages = readTopic(order, 5);
        assertThat(messages.stream().map(ConsumerRecord::partition).distinct()).hasSize(1);
    }
}
