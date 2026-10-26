-- Stock levels and the reservations that orders make against them.
-- A food with no row in stock_items is "not tracked": it is always available.

CREATE TABLE stock_items (
    -- MongoDB _id of the food, kept as text (the foods live in the Node backend)
    food_id     VARCHAR(64)  PRIMARY KEY,
    -- The CHECK is the last line of defence: stock can never go below zero
    quantity    INTEGER      NOT NULL CHECK (quantity >= 0),
    updated_at  TIMESTAMPTZ  NOT NULL
);

-- One reservation per order. RESERVED: stock is held (payment pending).
-- COMMITTED: the order was paid. RELEASED: the stock went back (payment failed or expired).
CREATE TABLE reservations (
    order_id    UUID         PRIMARY KEY,
    status      VARCHAR(12)  NOT NULL CHECK (status IN ('RESERVED', 'COMMITTED', 'RELEASED')),
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE reservation_items (
    id          BIGSERIAL    PRIMARY KEY,
    order_id    UUID         NOT NULL REFERENCES reservations (order_id) ON DELETE CASCADE,
    food_id     VARCHAR(64)  NOT NULL,
    quantity    INTEGER      NOT NULL CHECK (quantity > 0),
    UNIQUE (order_id, food_id)
);
