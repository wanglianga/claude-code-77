package com.elevator.rescue.controller;

import com.elevator.rescue.entity.PartReplacement;
import com.elevator.rescue.repository.PartReplacementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/parts")
@RequiredArgsConstructor
public class PartController {

    private final PartReplacementRepository partRepo;

    @GetMapping
    public List<PartReplacement> list(@RequestParam(required = false) Long elevatorId) {
        if (elevatorId != null) {
            return partRepo.findByElevatorIdOrderByReplaceDateDesc(elevatorId);
        }
        return partRepo.findAll();
    }

    @PostMapping
    public PartReplacement create(@RequestBody PartReplacement part) {
        part.setId(null);
        return partRepo.save(part);
    }
}
