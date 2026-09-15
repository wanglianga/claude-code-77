package com.elevator.rescue.repository;

import com.elevator.rescue.entity.RescueEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface RescueEventRepository extends JpaRepository<RescueEvent, Long> {

    List<RescueEvent> findAllByOrderByAlarmTimeDesc();

    List<RescueEvent> findByElevatorIdOrderByAlarmTimeDesc(Long elevatorId);

    List<RescueEvent> findByStatusOrderByAlarmTimeDesc(RescueEvent.EventStatus status);

    List<RescueEvent> findByAlarmTimeAfter(LocalDateTime time);

    long countByEventNoStartingWith(String prefix);

    long countByElevatorIdAndAlarmTimeAfter(Long elevatorId, LocalDateTime time);

    long countByAlarmTimeAfter(LocalDateTime time);
}
