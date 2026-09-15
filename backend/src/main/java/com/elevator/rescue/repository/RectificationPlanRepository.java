package com.elevator.rescue.repository;

import com.elevator.rescue.entity.RectificationPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RectificationPlanRepository extends JpaRepository<RectificationPlan, Long> {
    List<RectificationPlan> findByElevatorIdOrderByRequestedAtDesc(Long elevatorId);

    List<RectificationPlan> findAllByOrderByRequestedAtDesc();
}
