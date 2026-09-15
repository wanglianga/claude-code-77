package com.elevator.rescue.repository;

import com.elevator.rescue.entity.Elevator;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ElevatorRepository extends JpaRepository<Elevator, Long> {
    List<Elevator> findByBuildingId(Long buildingId);

    long countByStatus(Elevator.ElevatorStatus status);

    /** 事务内悲观锁：整改申请创建时锁定电梯行，串行化同一电梯的核验与申请 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Elevator e where e.id = :id")
    Optional<Elevator> findByIdForUpdate(@Param("id") Long id);
}
