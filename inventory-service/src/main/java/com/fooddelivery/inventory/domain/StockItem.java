package com.fooddelivery.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** How many units of one food are in stock. */
@Entity
@Table(name = "stock_items")
public class StockItem {

    @Id
    @Column(name = "food_id", length = 64)
    private String foodId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StockItem() {
        // for JPA
    }

    public StockItem(String foodId, int quantity, Instant now) {
        this.foodId = foodId;
        this.quantity = quantity;
        this.updatedAt = now;
    }

    public void setQuantity(int quantity, Instant now) {
        this.quantity = quantity;
        this.updatedAt = now;
    }

    public String getFoodId() {
        return foodId;
    }

    public int getQuantity() {
        return quantity;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
