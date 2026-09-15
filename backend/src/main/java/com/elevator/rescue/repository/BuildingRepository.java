package com.elevator.rescue.repository;

import com.elevator.rescue.entity.Building;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuildingRepository extends JpaRepository<Building, Long> {
}
