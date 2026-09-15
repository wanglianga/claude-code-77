package com.elevator.rescue.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

@Data
@Entity
@Table(name = "annual_inspections")
public class AnnualInspection {

    public enum InspectionResult {
        PASS,       // 合格
        RECTIFY,    // 整改后合格
        FAIL        // 不合格
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "elevator_id")
    private Elevator elevator;

    private LocalDate inspectionDate;

    private LocalDate nextInspectionDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private InspectionResult result;

    /** 检验机构 */
    private String inspector;

    private String reportNo;

    @Column(length = 1000)
    private String remark;
}
