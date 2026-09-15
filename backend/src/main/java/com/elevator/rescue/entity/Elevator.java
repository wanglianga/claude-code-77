package com.elevator.rescue.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

@Data
@Entity
@Table(name = "elevators")
public class Elevator {

    public enum ElevatorStatus {
        RUNNING,        // 正常运行
        STOPPED,        // 停梯
        MAINTENANCE     // 维保中
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 电梯编号（内部资产编号） */
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @ManyToOne(optional = false)
    @JoinColumn(name = "building_id")
    private Building building;

    @ManyToOne
    @JoinColumn(name = "company_id")
    private MaintenanceCompany company;

    private Integer floors;

    /** 门区位置，如“1 单元大堂东侧” */
    private String doorZone;

    private String brand;

    private String model;

    /** 位置描述，如“1 单元” */
    private String position;

    private LocalDate installDate;

    /** 下次年检日期 */
    private LocalDate nextInspectionDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ElevatorStatus status = ElevatorStatus.RUNNING;
}
