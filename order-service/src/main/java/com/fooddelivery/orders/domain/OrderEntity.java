package com.fooddelivery.orders.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * An order and its items. Items are only added through addItem(), so an
 * item can never exist without its order or with a bad quantity or price.
 *
 * The id is created here, before the order is saved. That lets the order hold
 * its stock reservation (which is keyed by this id) first, so an order that is
 * rejected for lack of stock never gets a database row at all.
 */
@Entity
@Table(name = "orders")
public class OrderEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    // Tells Spring Data that this object is new (INSERT), not an existing row (merge)
    @Transient
    private boolean isNew = true;

    @Column(name = "legacy_id", unique = true, length = 24)
    private String legacyId;

    @Column(name = "user_id", nullable = false, length = 24)
    private String userId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 10)
    private PaymentMethod paymentMethod;

    @Column(nullable = false)
    private boolean paid;

    @Column(name = "stripe_session_id")
    private String stripeSessionId;

    @Embedded
    private Address address;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // Saving or deleting an order saves or deletes its items too
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderItemEntity> items = new ArrayList<>();

    protected OrderEntity() {
        // for JPA
    }

    public OrderEntity(String userId, PaymentMethod paymentMethod, OrderStatus status,
                       Address address, Instant createdAt) {
        this(UUID.randomUUID(), userId, paymentMethod, status, address, createdAt);
    }

    public OrderEntity(UUID id, String userId, PaymentMethod paymentMethod, OrderStatus status,
                       Address address, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.paymentMethod = Objects.requireNonNull(paymentMethod, "paymentMethod");
        this.status = Objects.requireNonNull(status, "status");
        this.address = Objects.requireNonNull(address, "address");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public void addItem(String foodId, String name, BigDecimal unitPrice, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("price must not be negative");
        }
        items.add(new OrderItemEntity(this, foodId, name, unitPrice, quantity));
    }

    public void setAmount(BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        this.amount = amount;
    }

    public void setLegacyId(String legacyId) {
        this.legacyId = legacyId;
    }

    @Override
    public UUID getId() { return id; }

    @Override
    public boolean isNew() { return isNew; }

    @PostPersist
    @PostLoad
    void markNotNew() { this.isNew = false; }

    public String getLegacyId() { return legacyId; }
    public String getUserId() { return userId; }
    public BigDecimal getAmount() { return amount; }
    public OrderStatus getStatus() { return status; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public boolean isPaid() { return paid; }
    public String getStripeSessionId() { return stripeSessionId; }
    public Address getAddress() { return address; }
    public Instant getCreatedAt() { return createdAt; }

    /** Read-only view; use addItem() to change the items. */
    public List<OrderItemEntity> getItems() {
        return Collections.unmodifiableList(items);
    }
}
