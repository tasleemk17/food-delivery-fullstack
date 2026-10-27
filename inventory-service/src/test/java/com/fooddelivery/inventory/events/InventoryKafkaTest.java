package com.fooddelivery.inventory.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fooddelivery.inventory.TestcontainersConfiguration;
import com.fooddelivery.inventory.domain.ReservationRepository;
import com.fooddelivery.inventory.domain.ReservationStatus;
import com.fooddelivery.inventory.domain.StockItem;
import com.fooddelivery.inventory.domain.StockRepository;
import com.fooddelivery.inventory.service.InventoryService;
import com.fooddelivery.inventory.service.InventoryService.Line;
import java.time.Duration;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The real thing end to end: a real Kafka broker and PostgreSQL in Docker with the listener running.
 * Docker Desktop must be running.
 */
@SpringBootTest(properties = {
        "inventory-events.retry.initial-ms=100",
        "inventory-events.retry.max-ms=200",
        "inventory-events.retry.max-retries=2"
})
@Import(TestcontainersConfiguration.class)
@Testcontainers
class InventoryKafkaTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));

    @DynamicPropertySource
    static void kafkaAddress(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    KafkaTemplate<String, String> producer;
    @Autowired
    InventoryService inventory;
    @Autowired
    StockRepository stock;
    @Autowired
    ReservationRepository reservations;

    @BeforeEach
    void cleanDatabase() {
        reservations.deleteAll();
        stock.deleteAll();
    }

    private UUID reserveTwoOfFive() {
        inventory.setStock("A", 5);
        UUID order = UUID.randomUUID();
        inventory.reserve(order, List.of(new Line("A", 2)));
        return order;
    }

    private void publish(UUID order, String payload) {
        producer.send("order-events", order.toString(), payload);
        producer.flush();
    }

    private ReservationStatus statusOf(UUID order) {
        return reservations.findById(order).map(r -> r.getStatus()).orElse(null);
    }

    private int stockOfA() {
        return stock.findById("A").map(StockItem::getQuantity).orElseThrow();
    }

    /** Reads the inventory dead letter topic from the start for up to 20 s, until `payload` shows up. */
    private boolean deadLettered(String payload) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-reader-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<String> seen = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of("order-events.inventory.DLT"));
            long deadline = System.currentTimeMillis() + 20_000;
            while (System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> r : consumer.poll(Duration.ofMillis(500))) {
                    seen.add(r.value());
                }
                if (seen.contains(payload)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void aPaidEventOnTheTopicCommitsTheReservation() {
        UUID order = reserveTwoOfFive();

        publish(order, TestEvents.paid(order));

        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(statusOf(order)).isEqualTo(ReservationStatus.COMMITTED));
        assertThat(stockOfA()).isEqualTo(3);
    }

    @Test
    void aFailedPaymentEventGivesTheStockBackOnlyOnceEvenIfDeliveredTwice() {
        UUID order = reserveTwoOfFive();

        publish(order, TestEvents.failed(order));
        publish(order, TestEvents.failed(order));

        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(statusOf(order)).isEqualTo(ReservationStatus.RELEASED));
        assertThat(stockOfA()).isEqualTo(5);
    }

    @Test
    void aContradictoryEventIsParkedAndTheNextOneIsStillHandled() {
        UUID released = reserveTwoOfFive();
        inventory.release(released);
        String contradiction = TestEvents.paid(released); // paid after the stock was given back

        UUID next = UUID.randomUUID();
        inventory.reserve(next, List.of(new Line("A", 1)));

        publish(released, contradiction);
        publish(next, TestEvents.paid(next));

        // The one behind the bad message is not blocked ...
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(statusOf(next)).isEqualTo(ReservationStatus.COMMITTED));
        // ... and the bad one waits on the dead letter topic for a person
        assertThat(deadLettered(contradiction)).isTrue();
        assertThat(statusOf(released)).isEqualTo(ReservationStatus.RELEASED);
    }
}
