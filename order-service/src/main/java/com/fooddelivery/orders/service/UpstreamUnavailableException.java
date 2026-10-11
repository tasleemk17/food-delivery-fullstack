package com.fooddelivery.orders.service;

import org.springframework.http.HttpStatus;

/**
 * The Node backend could not be reached in time, or answered with a server error.
 * Callers get 503 and nothing is saved, so there is never a half-finished order.
 */
public class UpstreamUnavailableException extends OrderException {

    private final String detail;

    public UpstreamUnavailableException(String detail, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "Service temporarily unavailable, please try again");
        if (cause != null) {
            initCause(cause);
        }
        this.detail = detail;
    }

    /** What went wrong, for the logs only (never sent to the client). */
    public String detail() {
        return detail;
    }
}
