package com.fooddelivery.orders.events;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sends the events in the outbox to Kafka.
 *
 * - An event is marked sent only after Kafka confirms it (acks=all), so a crash
 *   in between means it is sent again, never lost. Listeners must therefore
 *   handle the same event twice (every event has a unique eventId).
 * - Events go out oldest first. If one fails, the rest wait, so the events of
 *   one order always arrive in the order they happened.
 * - If Kafka is down nothing is lost and orders keep working: the events wait
 *   in the table and go out when Kafka is back.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final Clock clock;
    private final String topic;
    private final int batchSize;
    private final long sendTimeoutMs;

    public OutboxPublisher(OutboxRepository outbox, KafkaTemplate<String, String> kafka, Clock clock,
                           @Value("${outbox.topic:order-events}") String topic,
                           @Value("${outbox.batch-size:50}") int batchSize,
                           @Value("${outbox.send-timeout-ms:5000}") long sendTimeoutMs) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.clock = clock;
        this.topic = topic;
        this.batchSize = batchSize;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    /** Sends one batch. Returns how many events Kafka confirmed. Never throws for a Kafka problem. */
    @Transactional
    public int publishPending() {
        List<OutboxEvent> batch = outbox.lockNextBatch(batchSize);
        int sent = 0;
        for (OutboxEvent event : batch) {
            try {
                // The order id is the key: all events of one order go to the same partition, in order
                ProducerRecord<String, String> record =
                        new ProducerRecord<>(topic, event.getAggregateId().toString(), event.getPayload());
                record.headers().add(new RecordHeader("event-type", event.getType().getBytes()));
                kafka.send(record).get(sendTimeoutMs, TimeUnit.MILLISECONDS);
                event.markPublished(Instant.now(clock));
                sent++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                event.recordFailedAttempt();
                break;
            } catch (Exception e) {
                event.recordFailedAttempt();
                log.warn("Could not send {} of order {} to Kafka (attempt {}): {}",
                        event.getType(), event.getAggregateId(), event.getAttempts(), rootMessage(e));
                break; // keep the order: do not send later events before this one
            }
        }
        return sent;
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage();
    }
}
