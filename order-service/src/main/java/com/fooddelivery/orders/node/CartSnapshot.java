package com.fooddelivery.orders.node;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * The saved cart as Node returns it: quantity per food id, plus the foods
 * that still exist with their current database price.
 */
public record CartSnapshot(Map<String, BigDecimal> cart, List<Food> foods) {

    public record Food(String id, String name, BigDecimal price) {
    }

    public CartSnapshot {
        cart = cart == null ? Map.of() : cart;
        foods = foods == null ? List.of() : foods;
    }
}
