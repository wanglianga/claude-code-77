package com.elevator.rescue.repository;

import com.elevator.rescue.entity.EventCall;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventCallRepository extends JpaRepository<EventCall, Long> {
    List<EventCall> findByEventIdOrderByCallTimeAsc(Long eventId);
}
