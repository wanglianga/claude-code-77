package com.elevator.rescue.controller;

import com.elevator.rescue.entity.DutySchedule;
import com.elevator.rescue.repository.DutyScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/duty-schedules")
@RequiredArgsConstructor
public class DutyController {

    private final DutyScheduleRepository dutyRepo;

    @GetMapping
    public List<DutySchedule> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        LocalDate s = start != null ? start : LocalDate.now().minusDays(7);
        LocalDate e = end != null ? end : LocalDate.now().plusDays(14);
        return dutyRepo.findByDutyDateBetweenOrderByDutyDateAsc(s, e);
    }

    @PostMapping
    public DutySchedule create(@RequestBody DutySchedule schedule) {
        schedule.setId(null);
        return dutyRepo.save(schedule);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        dutyRepo.deleteById(id);
    }
}
