package com.fooddelivery.orders.events;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * The oldest events that are not sent yet, locked for this transaction.
     * SKIP LOCKED means a second publisher instance would take other rows
     * instead of waiting, so two publishers never send the same event.
     */
    @Query(value = "select * from outbox_events where published_at is null "
            + "order by seq limit :limit for update skip locked", nativeQuery = true)
    List<OutboxEvent> lockNextBatch(@Param("limit") int limit);

    long countByPublishedAtIsNull();

    List<OutboxEvent> findByAggregateIdOrderBySeq(UUID aggregateId);
}
