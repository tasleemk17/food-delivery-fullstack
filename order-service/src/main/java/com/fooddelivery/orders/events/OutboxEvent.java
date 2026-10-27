package com.fooddelivery.orders.events;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One event waiting to be sent to Kafka (or already sent). See V2__create_outbox.sql. */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long seq;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(nullable = false)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private int attempts;

    protected OutboxEvent() {
        // for JPA
    }

    public OutboxEvent(UUID eventId, UUID aggregateId, String type, String payload, Instant createdAt) {
        this.eventId = eventId;
        this.aggregateId = aggregateId;
        this.type = type;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    /** Kafka confirmed the message. */
    public void markPublished(Instant now) {
        this.publishedAt = now;
        this.attempts++;
    }

    /** Sending failed; it stays in the outbox and is tried again. */
    public void recordFailedAttempt() {
        this.attempts++;
    }

    public Long getSeq() { return seq; }
    public UUID getEventId() { return eventId; }
    public UUID getAggregateId() { return aggregateId; }
    public String getType() { return type; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public int getAttempts() { return attempts; }
}
