package com.elevator.rescue.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "maintenance_contracts")
public class MaintenanceContract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 50)
    private String contractNo;

    @ManyToOne
    @JoinColumn(name = "elevator_id")
    private Elevator elevator;

    @ManyToOne
    @JoinColumn(name = "company_id")
    private MaintenanceCompany company;

    private LocalDate startDate;

    private LocalDate endDate;

    private BigDecimal annualFee;

    @Column(length = 2000)
    private String content;
}
