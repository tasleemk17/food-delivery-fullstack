package com.fooddelivery.orders.inventory;

import java.util.List;
import java.util.UUID;

/** What order-service needs from inventory-service, which owns the stock levels. */
public interface InventoryPort {

    /** One food and how many units an order wants. */
    record StockLine(String foodId, int quantity) {}

    /**
     * Holds stock for the order. All or nothing.
     *
     * @throws com.fooddelivery.orders.service.OutOfStockException if one food is short
     * @throws com.fooddelivery.orders.service.UpstreamUnavailableException if inventory-service cannot be reached
     */
    void reserve(UUID orderId, List<StockLine> lines);

    /**
     * Puts the stock back. Used only when saving the order fails right after the stock was held
     * (then there is no order and no event). In every other case inventory-service gives the stock
     * back by itself when it reads the ORDER_PAYMENT_FAILED event. Safe to repeat.
     */
    void release(UUID orderId);
}
