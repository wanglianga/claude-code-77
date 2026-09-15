package com.elevator.rescue.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

/**
 * 物业值班表
 */
@Data
@Entity
@Table(name = "duty_schedules")
public class DutySchedule {

    public enum Shift {
        DAY,    // 白班
        NIGHT   // 夜班
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    private LocalDate dutyDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Shift shift;

    /** 值班岗位，如“监控中心”“门岗” */
    private String position;

    private String remark;
}
