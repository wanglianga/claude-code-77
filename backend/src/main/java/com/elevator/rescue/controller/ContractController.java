package com.elevator.rescue.controller;

import com.elevator.rescue.entity.MaintenanceContract;
import com.elevator.rescue.repository.MaintenanceContractRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final MaintenanceContractRepository contractRepo;

    @GetMapping
    public List<MaintenanceContract> list(@RequestParam(required = false) Long elevatorId) {
        if (elevatorId != null) {
            return contractRepo.findByElevatorId(elevatorId);
        }
        return contractRepo.findAll();
    }

    @PostMapping
    public MaintenanceContract create(@RequestBody MaintenanceContract contract) {
        contract.setId(null);
        return contractRepo.save(contract);
    }

    @PutMapping("/{id}")
    public MaintenanceContract update(@PathVariable Long id, @RequestBody MaintenanceContract contract) {
        contract.setId(id);
        return contractRepo.save(contract);
    }
}
