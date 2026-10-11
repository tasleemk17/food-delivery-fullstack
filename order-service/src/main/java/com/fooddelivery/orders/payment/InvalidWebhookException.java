package com.fooddelivery.orders.payment;

import com.fooddelivery.orders.service.OrderException;
import org.springframework.http.HttpStatus;

/** Bad signature, or the webhook secret is not configured. */
public class InvalidWebhookException extends OrderException {
    public InvalidWebhookException(HttpStatus status, String message) {
        super(status, message);
    }
}
