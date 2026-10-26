package com.fooddelivery.orders.service;

import org.springframework.http.HttpStatus;

/**
 * inventory-service says one food has fewer units left than the order wants.
 * OrderService turns this into a message with the food's name for the customer.
 */
public class OutOfStockException extends OrderException {

    private final String foodId;
    private final int available;

    public OutOfStockException(String foodId, int available) {
        super(HttpStatus.CONFLICT, "Not enough stock");
        this.foodId = foodId;
        this.available = available;
    }

    public String foodId() {
        return foodId;
    }

    public int available() {
        return available;
    }
}
