package com.fooddelivery.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * inventory-service: owns food stock levels in its own PostgreSQL database.
 *
 * Orders reserve stock here before they are paid. A reservation is later
 * committed (paid) or released (payment failed or expired).
 */
@SpringBootApplication
public class InventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}
