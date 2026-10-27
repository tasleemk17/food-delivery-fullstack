-- The outbox: events are saved in the same transaction as the order change that
-- caused them, and a publisher sends them to Kafka afterwards. So an event can
-- never be lost (or sent for a change that was rolled back), even if Kafka is down.

CREATE TABLE outbox_events (
    -- seq gives one global order, so events of one order are sent in the order they happened
    seq           BIGSERIAL    PRIMARY KEY,
    event_id      UUID         NOT NULL UNIQUE,
    -- The order id. It is also the Kafka message key, so one order's events stay in order.
    aggregate_id  UUID         NOT NULL,
    type          VARCHAR(40)  NOT NULL,
    payload       TEXT         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    -- NULL until Kafka has confirmed the message
    published_at  TIMESTAMPTZ,
    attempts      INTEGER      NOT NULL DEFAULT 0
);

-- The publisher only ever looks at the events that are not sent yet
CREATE INDEX idx_outbox_unpublished ON outbox_events (seq) WHERE published_at IS NULL;
CREATE INDEX idx_outbox_aggregate ON outbox_events (aggregate_id);
