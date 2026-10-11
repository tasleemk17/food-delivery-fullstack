package com.fooddelivery.orders.pricing;

import com.fooddelivery.orders.service.OrderException;
import org.springframework.http.HttpStatus;

/** A food in the cart no longer exists (deleted by an admin). */
public class UnavailableItemException extends OrderException {
    public UnavailableItemException() {
        super(HttpStatus.CONFLICT,
                "Some items in your cart are no longer available and were removed. "
                        + "Please check your cart and try again.");
    }
}
