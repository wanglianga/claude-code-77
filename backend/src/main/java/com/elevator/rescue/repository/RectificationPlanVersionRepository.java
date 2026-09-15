package com.elevator.rescue.repository;

import com.elevator.rescue.entity.RectificationPlanVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RectificationPlanVersionRepository extends JpaRepository<RectificationPlanVersion, Long> {
    List<RectificationPlanVersion> findByPlanIdOrderByVersionNoAsc(Long planId);

    Optional<RectificationPlanVersion> findTopByPlanIdOrderByVersionNoDesc(Long planId);
}
