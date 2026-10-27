-- What the recommendations are built from. Everything here is derived from the order events,
-- so it could be thrown away and rebuilt by reading the Kafka topic from the start.

-- Every event that was already counted. Counting twice would give wrong numbers (unlike a
-- state change, a counter is not safe to repeat), so each event id is recorded first.
CREATE TABLE processed_events (
    event_id      UUID         PRIMARY KEY,
    processed_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- How many confirmed orders contained each food
CREATE TABLE food_stats (
    food_id  TEXT     PRIMARY KEY,
    name     TEXT     NOT NULL,
    orders   INTEGER  NOT NULL DEFAULT 0
);

-- How many confirmed orders contained both foods. Both directions are stored
-- ((a, b) and (b, a)), so "what goes with a?" is one simple indexed query.
CREATE TABLE food_pairs (
    food_id        TEXT     NOT NULL,
    other_food_id  TEXT     NOT NULL,
    together       INTEGER  NOT NULL DEFAULT 0,
    PRIMARY KEY (food_id, other_food_id)
);

CREATE INDEX idx_food_pairs_best ON food_pairs (food_id, together DESC);
