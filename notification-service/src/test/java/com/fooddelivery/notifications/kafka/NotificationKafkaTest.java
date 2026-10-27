package com.fooddelivery.notifications.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fooddelivery.notifications.TestEvents;
import com.fooddelivery.notifications.TestcontainersConfiguration;
import com.fooddelivery.notifications.mail.EmailSender;
import com.fooddelivery.notifications.mail.SmtpEmailSender;
import com.fooddelivery.notifications.notify.NotificationRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The real thing end to end: a real Kafka broker and PostgreSQL in Docker, the listener running,
 * and only the mail server faked. Docker Desktop must be running.
 */
@SpringBootTest(properties = {
        // Short waits between retries so the test does not take minutes
        "notifications.retry.initial-ms=100",
        "notifications.retry.max-ms=200",
        "notifications.retry.max-retries=3"
})
@Import(TestcontainersConfiguration.class)
@Testcontainers
class NotificationKafkaTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));

    @DynamicPropertySource
    static void kafkaAddress(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    KafkaTemplate<String, String> producer;
    @Autowired
    NotificationRepository notifications;
    @MockitoBean
    EmailSender sender;

    @BeforeEach
    void cleanDatabase() {
        notifications.deleteAll();
    }

    private void publish(String payload) {
        producer.send("order-events", TestEvents.ORDER_ID, payload);
        producer.flush();
    }

    /** Reads the dead letter topic from the start for up to 20 s, until `payload` shows up. */
    private boolean deadLettered(String payload) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-reader-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<String> seen = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of("order-events.notification.DLT"));
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
    void anEventOnTheTopicSendsAnEmailAndTheSameEventAgainDoesNot() {
        UUID first = UUID.randomUUID();
        publish(TestEvents.codPlaced(first));
        publish(TestEvents.codPlaced(first)); // delivered twice
        publish(TestEvents.stripePaid(UUID.randomUUID()));

        // Messages are handled in order, so once the third is done the duplicate was handled too
        verify(sender, timeout(20_000).times(2)).send(any());
        assertThat(notifications.count()).isEqualTo(2);
    }

    @Test
    void aBrokenMessageIsParkedAndDoesNotBlockTheOnesBehindIt() {
        String garbage = "this is not an order event " + UUID.randomUUID();
        publish(garbage);
        publish(TestEvents.stripePaid(UUID.randomUUID()));

        // The good message behind the broken one is still handled ...
        verify(sender, timeout(20_000).times(1)).send(any());
        // ... and the broken one waits on the dead letter topic for a person to look at it
        assertThat(deadLettered(garbage)).isTrue();
    }

    @Test
    void aMailServerThatIsDownForAMomentJustDelaysTheEmail() {
        doThrow(new SmtpEmailSender.EmailSendException("connection refused", null))
                .doThrow(new SmtpEmailSender.EmailSendException("connection refused", null))
                .doNothing()
                .when(sender).send(any());

        publish(TestEvents.codPlaced(UUID.randomUUID()));

        // Failed twice, went through the third time
        verify(sender, timeout(20_000).times(3)).send(any());
        assertThat(notifications.count()).isEqualTo(1);
        doNothing().when(sender).send(any());
    }
}
