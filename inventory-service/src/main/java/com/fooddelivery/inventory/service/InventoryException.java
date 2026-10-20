package com.fooddelivery.inventory.service;

import org.springframework.http.HttpStatus;

/** A problem the caller should hear about, with the HTTP status to answer with. */
public class InventoryException extends RuntimeException {

    private final HttpStatus status;

    public InventoryException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
