package com.fooddelivery.notifications.notify;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<NotificationRecord, Long> {

    boolean existsByEventId(UUID eventId);

    List<NotificationRecord> findByOrderIdOrderBySentAt(UUID orderId);
}
