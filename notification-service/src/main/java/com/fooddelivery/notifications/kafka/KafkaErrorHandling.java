package com.fooddelivery.notifications.kafka;

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
 * - A temporary problem (the mail server is down): try again after 1 s, 2 s, 4 s ... up to
 *   30 s apart, 8 times. Messages behind it wait, so the order of events is kept.
 * - A message that can never work (not valid JSON, no eventId, a bad email address): no
 *   point retrying, it goes straight to the dead letter topic.
 * - Once the retries run out the message is moved to "order-events.DLT" and the service
 *   carries on with the next one, so one bad message can never block everything behind it.
 *   Someone can look at the dead letter topic and replay the message later.
 */
@Configuration
class KafkaErrorHandling {

    @Bean
    DefaultErrorHandler errorHandler(KafkaTemplate<String, String> kafka,
                                     @Value("${notifications.retry.initial-ms:1000}") long initialMs,
                                     @Value("${notifications.retry.max-ms:30000}") long maxMs,
                                     @Value("${notifications.retry.max-retries:8}") int maxRetries) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(maxRetries);
        backOff.setInitialInterval(initialMs);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(maxMs);

        // Parks the failed message on "<topic>.DLT". The name is spelled out here because Spring Kafka's
        // own default changed between versions ("-dlt" now). Partition -1: the producer picks one from the
        // key, so it does not matter how many partitions the dead letter topic has.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafka,
                (record, exception) -> new TopicPartition(record.topic() + ".DLT", -1));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }
}
