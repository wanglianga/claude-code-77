package com.elevator.rescue.controller;

import com.elevator.rescue.entity.Building;
import com.elevator.rescue.repository.BuildingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/buildings")
@RequiredArgsConstructor
public class BuildingController {

    private final BuildingRepository buildingRepo;

    @GetMapping
    public List<Building> list() {
        return buildingRepo.findAll();
    }

    @PostMapping
    public Building create(@RequestBody Building building) {
        building.setId(null);
        return buildingRepo.save(building);
    }

    @PutMapping("/{id}")
    public Building update(@PathVariable Long id, @RequestBody Building building) {
        building.setId(id);
        return buildingRepo.save(building);
    }
}
