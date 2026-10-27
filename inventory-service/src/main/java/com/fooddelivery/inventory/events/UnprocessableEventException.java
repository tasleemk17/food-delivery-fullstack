package com.fooddelivery.inventory.events;

/**
 * An event that can never be handled, however often it is tried: it is not a valid order event,
 * or it asks for something that contradicts what already happened (for example, to confirm
 * stock that was already given back). It is not retried; it is parked for a person to look at.
 */
public class UnprocessableEventException extends RuntimeException {

    public UnprocessableEventException(String message) {
        super(message);
    }

    public UnprocessableEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
