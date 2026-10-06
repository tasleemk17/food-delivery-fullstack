-- Orders and their line items. Money is NUMERIC(10,2): exact decimal, never floating point.

CREATE TABLE orders (
    id                UUID          PRIMARY KEY,
    -- MongoDB _id of an order copied over from the Node backend (Step 7); NULL for new orders
    legacy_id         VARCHAR(24)   UNIQUE,
    user_id           VARCHAR(24)   NOT NULL,
    amount            NUMERIC(10,2) NOT NULL CHECK (amount >= 0),
    status            VARCHAR(30)   NOT NULL CHECK (status IN
                          ('PAYMENT_PENDING', 'PAYMENT_FAILED', 'FOOD_PROCESSING', 'OUT_FOR_DELIVERY', 'DELIVERED')),
    payment_method    VARCHAR(10)   NOT NULL CHECK (payment_method IN ('COD', 'STRIPE')),
    paid              BOOLEAN       NOT NULL DEFAULT FALSE,
    stripe_session_id VARCHAR(255),

    -- Delivery address, stored with the order so later profile changes do not rewrite history
    first_name        VARCHAR(200)  NOT NULL,
    last_name         VARCHAR(200)  NOT NULL,
    email             VARCHAR(200)  NOT NULL,
    street            VARCHAR(200)  NOT NULL,
    city              VARCHAR(200)  NOT NULL,
    state             VARCHAR(200)  NOT NULL,
    zipcode           VARCHAR(200)  NOT NULL,
    country           VARCHAR(200)  NOT NULL,
    phone             VARCHAR(200)  NOT NULL,

    created_at        TIMESTAMPTZ   NOT NULL
);

-- "My Orders" looks up by user, newest first
CREATE INDEX idx_orders_user_created ON orders (user_id, created_at DESC);

CREATE TABLE order_items (
    id         BIGSERIAL     PRIMARY KEY,
    order_id   UUID          NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    food_id    VARCHAR(24)   NOT NULL,
    -- Name and price are copied at order time, so editing the menu later
    -- does not change what an old order cost
    name       VARCHAR(200)  NOT NULL,
    unit_price NUMERIC(10,2) NOT NULL CHECK (unit_price >= 0),
    quantity   INTEGER       NOT NULL CHECK (quantity > 0)
);

CREATE INDEX idx_order_items_order ON order_items (order_id);
