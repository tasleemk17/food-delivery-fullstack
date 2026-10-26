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

    /** The order was paid: the stock stays taken. Safe to repeat. */
    void commit(UUID orderId);

    /** The order will not be paid: put the stock back. Safe to repeat. */
    void release(UUID orderId);
}
