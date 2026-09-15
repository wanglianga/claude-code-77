package com.elevator.rescue.repository;

import com.elevator.rescue.entity.OwnerFollowup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OwnerFollowupRepository extends JpaRepository<OwnerFollowup, Long> {
    List<OwnerFollowup> findByEventIdOrderByFollowupTimeAsc(Long eventId);
}
