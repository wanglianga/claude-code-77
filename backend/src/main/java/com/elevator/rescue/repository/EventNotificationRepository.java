package com.elevator.rescue.repository;

import com.elevator.rescue.entity.EventNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventNotificationRepository extends JpaRepository<EventNotification, Long> {
    List<EventNotification> findByEventIdOrderByNotifiedAtAsc(Long eventId);
}
