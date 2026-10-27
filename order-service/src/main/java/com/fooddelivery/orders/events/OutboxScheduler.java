package com.fooddelivery.orders.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Runs the publisher about once a second. Switch it off with
 * outbox.publisher.enabled=false (tests do, and call the publisher themselves).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
class OutboxScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxScheduler.class);

    private final OutboxPublisher publisher;

    OutboxScheduler(OutboxPublisher publisher) {
        this.publisher = publisher;
    }

    // fixedDelay: the next run starts only after the last one finished, so runs never overlap
    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:1000}")
    void publish() {
        try {
            publisher.publishPending();
        } catch (RuntimeException e) {
            log.error("Outbox publisher failed", e);
        }
    }
}
