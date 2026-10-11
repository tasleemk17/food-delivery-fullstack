package com.fooddelivery.orders.service;

import org.springframework.http.HttpStatus;

/** A problem the caller should hear about, with the HTTP status to answer with. */
public class OrderException extends RuntimeException {

    private final HttpStatus status;

    public OrderException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
