package com.fooddelivery.orders.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    // @EntityGraph loads each order's items in the same query. Without it,
    // Hibernate would run one extra query per order (the "N+1 problem").

    @EntityGraph(attributePaths = "items")
    List<OrderEntity> findByUserIdOrderByCreatedAtDesc(String userId);

    @EntityGraph(attributePaths = "items")
    List<OrderEntity> findAllByOrderByCreatedAtAsc();

    @EntityGraph(attributePaths = "items")
    Optional<OrderEntity> findWithItemsById(UUID id);

    // The updates below are single conditional UPDATE statements. The database
    // decides who wins when two requests race, so "mark paid" is idempotent:
    // exactly one caller sees a count of 1.

    /** Marks a Stripe order paid. Returns 1 if this call did it, 0 if it was already paid. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update OrderEntity o set o.paid = true, o.status = com.fooddelivery.orders.domain.OrderStatus.FOOD_PROCESSING "
            + "where o.id = :id and o.paid = false")
    int markPaidIfUnpaid(@Param("id") UUID id);

    /** Marks an unpaid order's payment as failed (expired session). Never touches a paid order. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update OrderEntity o set o.status = com.fooddelivery.orders.domain.OrderStatus.PAYMENT_FAILED "
            + "where o.id = :id and o.paid = false")
    int markFailedIfUnpaid(@Param("id") UUID id);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update OrderEntity o set o.stripeSessionId = :sessionId where o.id = :id")
    int setStripeSessionId(@Param("id") UUID id, @Param("sessionId") String sessionId);

    /** Admin status change. A Stripe order that is not paid yet cannot be moved forward. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update OrderEntity o set o.status = :status where o.id = :id "
            + "and (o.paymentMethod <> com.fooddelivery.orders.domain.PaymentMethod.STRIPE or o.paid = true)")
    int updateStatusIfPayable(@Param("id") UUID id, @Param("status") OrderStatus status);
}
