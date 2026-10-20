package com.fooddelivery.inventory.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The stock one order is holding. Keyed by the order's id, so repeating a call is harmless. */
@Entity
@Table(name = "reservations")
public class ReservationEntity {

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ReservationItemEntity> items = new ArrayList<>();

    protected ReservationEntity() {
        // for JPA
    }

    public ReservationEntity(UUID orderId, Instant now) {
        this.orderId = orderId;
        this.status = ReservationStatus.RESERVED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void addItem(String foodId, int quantity) {
        items.add(new ReservationItemEntity(this, foodId, quantity));
    }

    public void changeStatus(ReservationStatus newStatus, Instant now) {
        this.status = newStatus;
        this.updatedAt = now;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public List<ReservationItemEntity> getItems() {
        return items;
    }
}
