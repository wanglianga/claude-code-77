package com.elevator.rescue.repository;

import com.elevator.rescue.entity.PartReplacement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PartReplacementRepository extends JpaRepository<PartReplacement, Long> {
    List<PartReplacement> findByElevatorIdOrderByReplaceDateDesc(Long elevatorId);

    List<PartReplacement> findByEventId(Long eventId);
}
