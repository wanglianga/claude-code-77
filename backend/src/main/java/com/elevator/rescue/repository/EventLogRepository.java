package com.elevator.rescue.repository;

import com.elevator.rescue.entity.EventLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventLogRepository extends JpaRepository<EventLog, Long> {
    List<EventLog> findByEventIdOrderByCreatedAtAsc(Long eventId);
}
