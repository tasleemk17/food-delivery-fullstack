-- Inserts one sample order for user 64b000000000000000000001, for manual testing in Step 5.
-- Run from the project folder:
--   docker exec -i food-postgres psql -U orders -d orders < order-service/dev/seed-sample-order.sql

WITH new_order AS (
    INSERT INTO orders (id, user_id, amount, status, payment_method, paid,
                        first_name, last_name, email, street, city, state, zipcode, country, phone, created_at)
    VALUES (gen_random_uuid(), '64b000000000000000000001', 330.00, 'FOOD_PROCESSING', 'COD', FALSE,
            'Test', 'User', 'test@example.com', '1 MG Road', 'Pune', 'MH', '411001', 'India', '9999999999', NOW())
    RETURNING id
)
INSERT INTO order_items (order_id, food_id, name, unit_price, quantity)
SELECT id, '64b0000000000000000000f1', 'Cupcake', 140.00, 2 FROM new_order;

SELECT o.id, o.user_id, o.amount, o.status, i.name, i.quantity
FROM orders o JOIN order_items i ON i.order_id = o.id
ORDER BY o.created_at DESC
LIMIT 5;
