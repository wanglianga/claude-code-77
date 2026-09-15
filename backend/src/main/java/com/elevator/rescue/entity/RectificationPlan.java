package com.elevator.rescue.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 反复故障电梯的整改方案（物业要求 → 维保提交 → 复检）
 */
@Data
@Entity
@Table(name = "rectification_plans")
public class RectificationPlan {

    public enum PlanStatus {
        REQUESTED,          // 物业已要求提交
        SUBMITTED,          // 维保已提交方案
        RECHECK_PASSED,     // 复检通过（电梯恢复运行）
        RECHECK_FAILED      // 复检未通过（继续停梯，需重新提交）
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "elevator_id")
    private Elevator elevator;

    /** 触发的困人事件（可空） */
    private Long eventId;

    /** 维保单位名称（冗余，便于展示） */
    private String companyName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanStatus status = PlanStatus.REQUESTED;

    // ---------- 物业要求 ----------
    private String requestedBy;

    private LocalDateTime requestedAt = LocalDateTime.now();

    @Column(length = 1000)
    private String requestNote;

    // ---------- 维保提交方案 ----------
    /** 需更换的配件 */
    @Column(length = 1000)
    private String parts;

    /** 配件预计到货日期 */
    private LocalDate expectedArrival;

    /** 复检人 */
    private String recheckInspector;

    /** 业主公告发布时间 */
    private LocalDateTime noticePublishTime;

    @Column(length = 2000)
    private String planDetail;

    private String submittedBy;

    private LocalDateTime submittedAt;

    // ---------- 复检 ----------
    private LocalDateTime recheckedAt;

    @Column(length = 500)
    private String recheckResult;

    private String recheckedBy;
}
