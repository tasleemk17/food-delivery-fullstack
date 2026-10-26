package com.fooddelivery.inventory.service;

import org.springframework.http.HttpStatus;

/** Not enough units left of one food. Says which food, so the caller can tell the customer. */
public class InsufficientStockException extends InventoryException {

    private final String foodId;
    private final int available;

    public InsufficientStockException(String foodId, int available) {
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
