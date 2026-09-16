package com.elevator.rescue.repository;

import com.elevator.rescue.entity.AssistancePlanLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssistancePlanLinkRepository extends JpaRepository<AssistancePlanLink, Long> {
    List<AssistancePlanLink> findByRectificationPlanId(Long rectificationPlanId);

    List<AssistancePlanLink> findByAssistanceIdOrderByLinkedAtAsc(Long assistanceId);
}
