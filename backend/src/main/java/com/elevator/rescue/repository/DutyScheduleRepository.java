package com.elevator.rescue.repository;

import com.elevator.rescue.entity.DutySchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DutyScheduleRepository extends JpaRepository<DutySchedule, Long> {
    List<DutySchedule> findByDutyDateBetweenOrderByDutyDateAsc(LocalDate start, LocalDate end);
}
