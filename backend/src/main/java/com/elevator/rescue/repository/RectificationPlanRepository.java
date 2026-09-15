package com.elevator.rescue.repository;

import com.elevator.rescue.entity.RectificationPlan;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RectificationPlanRepository extends JpaRepository<RectificationPlan, Long> {
    List<RectificationPlan> findByElevatorIdOrderByRequestedAtDesc(Long elevatorId);

    List<RectificationPlan> findAllByOrderByRequestedAtDesc();

    /** 事务内悲观锁：提交新版本 / 登记复检时串行化同一申请的状态推进 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from RectificationPlan p where p.id = :id")
    Optional<RectificationPlan> findByIdForUpdate(@Param("id") Long id);
}
