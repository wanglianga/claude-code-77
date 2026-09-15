package com.elevator.rescue.repository;

import com.elevator.rescue.entity.MaintenanceCompany;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceCompanyRepository extends JpaRepository<MaintenanceCompany, Long> {
}
