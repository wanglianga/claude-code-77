package com.elevator.rescue.repository;

import com.elevator.rescue.entity.ElderlyAssistance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ElderlyAssistanceRepository extends JpaRepository<ElderlyAssistance, Long> {
    List<ElderlyAssistance> findByBuildingIdOrderByCreatedAtDesc(Long buildingId);

    List<ElderlyAssistance> findAllByOrderByCreatedAtDesc();
}
