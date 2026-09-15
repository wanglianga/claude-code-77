package com.elevator.rescue.repository;

import com.elevator.rescue.entity.RecheckRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecheckRecordRepository extends JpaRepository<RecheckRecord, Long> {
    List<RecheckRecord> findByPlanIdOrderByRecheckedAtAsc(Long planId);

    List<RecheckRecord> findByVersionIdOrderByRecheckedAtAsc(Long versionId);
}
