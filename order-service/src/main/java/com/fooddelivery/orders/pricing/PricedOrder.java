package com.fooddelivery.orders.pricing;

import java.math.BigDecimal;
import java.util.List;

/** A cart priced on the server: items with current prices, plus the total. */
public record PricedOrder(List<PricedItem> items, BigDecimal amount) {

    public record PricedItem(String foodId, String name, BigDecimal unitPrice, int quantity) {}
}
