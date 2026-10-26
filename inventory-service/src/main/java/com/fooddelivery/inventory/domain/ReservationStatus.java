package com.fooddelivery.inventory.domain;

/** Where a reservation is in its life. */
public enum ReservationStatus {
    /** Stock is held while the order waits for payment. */
    RESERVED,
    /** The order was paid; the stock stays taken. */
    COMMITTED,
    /** The stock went back (payment failed or expired). */
    RELEASED
}
