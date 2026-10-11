package com.fooddelivery.orders.payment;

import com.fooddelivery.orders.service.OrderException;
import org.springframework.http.HttpStatus;

/** Stripe refused or failed a request. */
public class PaymentProviderException extends OrderException {
    public PaymentProviderException(String message, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, "Payment provider error, please try again");
        if (cause != null) {
            initCause(cause);
        }
        this.detail = message;
    }

    private final String detail;

    public String detail() {
        return detail;
    }
}
