package com.elevator.rescue.repository;

import com.elevator.rescue.entity.Elevator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ElevatorRepository extends JpaRepository<Elevator, Long> {
    List<Elevator> findByBuildingId(Long buildingId);

    long countByStatus(Elevator.ElevatorStatus status);
}
