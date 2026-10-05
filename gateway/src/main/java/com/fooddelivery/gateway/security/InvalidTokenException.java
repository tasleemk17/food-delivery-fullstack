package com.fooddelivery.gateway.security;

/** Thrown when a token is missing, forged, expired or malformed. */
public class InvalidTokenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidTokenException(String reason) {
        super(reason);
    }
}
