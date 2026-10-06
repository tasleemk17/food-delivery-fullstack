package com.fooddelivery.orders.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    // @EntityGraph loads each order's items in the same query. Without it,
    // Hibernate would run one extra query per order (the "N+1 problem").

    @EntityGraph(attributePaths = "items")
    List<OrderEntity> findByUserIdOrderByCreatedAtDesc(String userId);

    @EntityGraph(attributePaths = "items")
    List<OrderEntity> findAllByOrderByCreatedAtAsc();
}
