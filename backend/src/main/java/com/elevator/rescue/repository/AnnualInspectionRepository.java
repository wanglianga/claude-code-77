package com.elevator.rescue.repository;

import com.elevator.rescue.entity.AnnualInspection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnnualInspectionRepository extends JpaRepository<AnnualInspection, Long> {
    List<AnnualInspection> findByElevatorIdOrderByInspectionDateDesc(Long elevatorId);
}
