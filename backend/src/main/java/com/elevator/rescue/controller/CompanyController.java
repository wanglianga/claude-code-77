package com.elevator.rescue.controller;

import com.elevator.rescue.entity.MaintenanceCompany;
import com.elevator.rescue.repository.MaintenanceCompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final MaintenanceCompanyRepository companyRepo;

    @GetMapping
    public List<MaintenanceCompany> list() {
        return companyRepo.findAll();
    }

    @PostMapping
    public MaintenanceCompany create(@RequestBody MaintenanceCompany company) {
        company.setId(null);
        return companyRepo.save(company);
    }

    @PutMapping("/{id}")
    public MaintenanceCompany update(@PathVariable Long id, @RequestBody MaintenanceCompany company) {
        company.setId(id);
        return companyRepo.save(company);
    }
}
