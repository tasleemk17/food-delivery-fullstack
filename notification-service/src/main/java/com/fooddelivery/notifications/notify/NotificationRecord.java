package com.fooddelivery.notifications.notify;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** An email that was sent. The unique event id is what makes processing an event twice harmless. */
@Entity
@Table(name = "notifications")
public class NotificationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "user_id", nullable = false, length = 24)
    private String userId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(nullable = false, length = 200)
    private String recipient;

    @Column(nullable = false, length = 300)
    private String subject;

    @Column(nullable = false)
    private String body;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    protected NotificationRecord() {
        // for JPA
    }

    public NotificationRecord(UUID eventId, UUID orderId, String userId, String eventType,
                              String recipient, String subject, String body, Instant sentAt) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.userId = userId;
        this.eventType = eventType;
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
        this.sentAt = sentAt;
    }

    public Long getId() { return id; }
    public UUID getEventId() { return eventId; }
    public UUID getOrderId() { return orderId; }
    public String getUserId() { return userId; }
    public String getEventType() { return eventType; }
    public String getRecipient() { return recipient; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public Instant getSentAt() { return sentAt; }
}
