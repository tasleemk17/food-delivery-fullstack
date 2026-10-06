package com.fooddelivery.orders;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * order-service: stores orders in PostgreSQL and serves the order APIs.
 *
 * Step 5: read endpoints only (a user's orders, the admin order list).
 * Step 6 adds placing orders and payments; Step 7 moves traffic here
 * from the Node backend.
 */
@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
