package com.fooddelivery.notifications.notify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.notifications.events.OrderEvent;
import com.fooddelivery.notifications.mail.EmailMessage;
import com.fooddelivery.notifications.mail.EmailSender;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles one order event: sends the email it calls for, exactly once.
 *
 * - The record of the email and the sending happen in ONE transaction. If the mail
 *   server is down the record is rolled back and the exception makes Kafka deliver
 *   the event again, so the email is retried and never lost.
 * - If the same event arrives twice (Kafka delivers at least once) the stored
 *   event id is found and nothing is sent again.
 * - A message that is not a valid order event throws IllegalArgumentException.
 *   That is not worth retrying; it is parked on the dead letter topic.
 */
@Service
public class OrderEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(OrderEventProcessor.class);

    /** What happened to one event. */
    public enum Result { SENT, DUPLICATE, SKIPPED }

    private final ObjectMapper mapper;
    private final EmailComposer composer;
    private final EmailSender sender;
    private final NotificationRepository notifications;
    private final Clock clock;

    public OrderEventProcessor(ObjectMapper mapper, EmailComposer composer, EmailSender sender,
                               NotificationRepository notifications, Clock clock) {
        this.mapper = mapper;
        this.composer = composer;
        this.sender = sender;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional
    public Result process(String json) {
        OrderEvent event = parse(json);

        Optional<EmailMessage> email = composer.compose(event);
        if (email.isEmpty()) {
            log.debug("No email for {} of order {}", event.type(), event.order() == null ? null : event.order().orderId());
            return Result.SKIPPED;
        }
        if (notifications.existsByEventId(event.eventId())) {
            log.info("Event {} was already handled, not sending again", event.eventId());
            return Result.DUPLICATE;
        }

        EmailMessage message = email.get();
        // Saved first and flushed: the unique event id is the final guard against a duplicate
        notifications.saveAndFlush(new NotificationRecord(event.eventId(), event.order().orderId(),
                event.order().userId(), event.type(), message.to(), message.subject(), message.body(),
                Instant.now(clock)));
        sender.send(message); // throws if the mail server is down: everything above is rolled back
        log.info("Sent '{}' to {} for event {}", message.subject(), message.to(), event.eventId());
        return Result.SENT;
    }

    private OrderEvent parse(String json) {
        OrderEvent event;
        try {
            event = mapper.readValue(json, OrderEvent.class);
        } catch (JsonProcessingException | RuntimeException e) {
            throw new IllegalArgumentException("Not a valid order event", e);
        }
        if (event == null || event.eventId() == null) {
            throw new IllegalArgumentException("The order event has no eventId");
        }
        return event;
    }
}
