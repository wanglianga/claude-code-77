package com.elevator.rescue.repository;

import com.elevator.rescue.entity.MaintenanceContract;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaintenanceContractRepository extends JpaRepository<MaintenanceContract, Long> {
    List<MaintenanceContract> findByElevatorId(Long elevatorId);

    List<MaintenanceContract> findByCompanyId(Long companyId);
}
