package com.elevator.rescue.repository;

import com.elevator.rescue.entity.AssistanceEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssistanceEventRepository extends JpaRepository<AssistanceEvent, Long> {
    List<AssistanceEvent> findByAssistanceIdOrderByCreatedAtAsc(Long assistanceId);
}
