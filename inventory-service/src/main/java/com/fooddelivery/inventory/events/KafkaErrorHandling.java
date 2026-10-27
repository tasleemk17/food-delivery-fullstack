package com.fooddelivery.inventory.events;

import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * What happens when handling a message fails.
 *
 * - A temporary problem (the database is down): try again after 1 s, 2 s, 4 s ... up to 30 s apart,
 *   8 times. Messages behind it wait, so the order of events is kept.
 * - A message that can never work (not valid JSON, or it contradicts what already happened): no point
 *   retrying, it goes straight to the dead letter topic.
 * - Once the retries run out the message is moved to "order-events.inventory.DLT" and the service
 *   carries on with the next one, so one bad message can never block the stock updates behind it.
 *
 * The dead letter topic has this service's name in it, so a person can tell whose failure it is
 * (the notification-service has its own).
 */
@Configuration
class KafkaErrorHandling {

    @Bean
    DefaultErrorHandler errorHandler(KafkaTemplate<String, String> kafka,
                                     @Value("${inventory-events.retry.initial-ms:1000}") long initialMs,
                                     @Value("${inventory-events.retry.max-ms:30000}") long maxMs,
                                     @Value("${inventory-events.retry.max-retries:8}") int maxRetries) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(maxRetries);
        backOff.setInitialInterval(initialMs);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(maxMs);

        // Partition -1: the producer picks one from the key, so the number of partitions does not matter
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafka,
                (record, exception) -> new TopicPartition(record.topic() + ".inventory.DLT", -1));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(UnprocessableEventException.class);
        return handler;
    }
}
