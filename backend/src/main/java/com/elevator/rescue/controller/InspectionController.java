package com.elevator.rescue.controller;

import com.elevator.rescue.entity.AnnualInspection;
import com.elevator.rescue.repository.AnnualInspectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inspections")
@RequiredArgsConstructor
public class InspectionController {

    private final AnnualInspectionRepository inspectionRepo;

    @GetMapping
    public List<AnnualInspection> list(@RequestParam(required = false) Long elevatorId) {
        if (elevatorId != null) {
            return inspectionRepo.findByElevatorIdOrderByInspectionDateDesc(elevatorId);
        }
        return inspectionRepo.findAll();
    }

    @PostMapping
    public AnnualInspection create(@RequestBody AnnualInspection inspection) {
        inspection.setId(null);
        return inspectionRepo.save(inspection);
    }
}
