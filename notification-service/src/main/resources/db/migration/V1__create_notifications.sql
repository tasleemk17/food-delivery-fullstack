-- One row per email that was sent. The unique event_id is what stops the same
-- order event from sending the same email twice (Kafka may deliver a message more than once).

CREATE TABLE notifications (
    id          BIGSERIAL     PRIMARY KEY,
    event_id    UUID          NOT NULL UNIQUE,
    order_id    UUID          NOT NULL,
    user_id     VARCHAR(24)   NOT NULL,
    event_type  VARCHAR(40)   NOT NULL,
    recipient   VARCHAR(200)  NOT NULL,
    subject     VARCHAR(300)  NOT NULL,
    body        TEXT          NOT NULL,
    sent_at     TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_notifications_order ON notifications (order_id, sent_at);
CREATE INDEX idx_notifications_user ON notifications (user_id, sent_at DESC);
