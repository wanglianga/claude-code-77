package com.elevator.rescue.controller;

import com.elevator.rescue.entity.Elevator;
import com.elevator.rescue.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/elevators")
@RequiredArgsConstructor
public class ElevatorController {

    private final ElevatorRepository elevatorRepo;
    private final MaintenanceContractRepository contractRepo;
    private final AnnualInspectionRepository inspectionRepo;
    private final PartReplacementRepository partRepo;
    private final RescueEventRepository eventRepo;

    @GetMapping
    public List<Elevator> list(@RequestParam(required = false) Long buildingId) {
        if (buildingId != null) {
            return elevatorRepo.findByBuildingId(buildingId);
        }
        return elevatorRepo.findAll();
    }

    @PostMapping
    public Elevator create(@RequestBody Elevator elevator) {
        elevatorRepo.findAll().stream()
                .filter(e -> e.getCode().equals(elevator.getCode()))
                .findFirst()
                .ifPresent(e -> {
                    throw new IllegalArgumentException("电梯编号已存在: " + elevator.getCode());
                });
        elevator.setId(null);
        return elevatorRepo.save(elevator);
    }

    @PutMapping("/{id}")
    public Elevator update(@PathVariable Long id, @RequestBody Elevator elevator) {
        Elevator existing = elevatorRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("电梯不存在"));
        elevator.setId(existing.getId());
        // 停梯计时：状态切换到停梯时记录开始时间，恢复时清空
        if (elevator.getStatus() == Elevator.ElevatorStatus.STOPPED) {
            elevator.setStoppedSince(existing.getStoppedSince() != null
                    ? existing.getStoppedSince() : java.time.LocalDateTime.now());
        } else {
            elevator.setStoppedSince(null);
        }
        return elevatorRepo.save(elevator);
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        Elevator elevator = elevatorRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("电梯不存在"));
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("elevator", elevator);
        detail.put("contracts", contractRepo.findByElevatorId(id));
        detail.put("inspections", inspectionRepo.findByElevatorIdOrderByInspectionDateDesc(id));
        detail.put("parts", partRepo.findByElevatorIdOrderByReplaceDateDesc(id));
        detail.put("events", eventRepo.findByElevatorIdOrderByAlarmTimeDesc(id));
        return detail;
    }
}
