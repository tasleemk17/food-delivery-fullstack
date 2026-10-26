package com.fooddelivery.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One food and how many units a reservation holds. */
@Entity
@Table(name = "reservation_items")
public class ReservationItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private ReservationEntity reservation;

    @Column(name = "food_id", nullable = false, length = 64)
    private String foodId;

    @Column(nullable = false)
    private int quantity;

    protected ReservationItemEntity() {
        // for JPA
    }

    ReservationItemEntity(ReservationEntity reservation, String foodId, int quantity) {
        this.reservation = reservation;
        this.foodId = foodId;
        this.quantity = quantity;
    }

    public String getFoodId() {
        return foodId;
    }

    public int getQuantity() {
        return quantity;
    }
}
