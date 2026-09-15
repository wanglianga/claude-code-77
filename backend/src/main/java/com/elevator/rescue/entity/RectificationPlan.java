package com.elevator.rescue.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 反复故障电梯的停梯整改申请（一单到底）。
 *
 * <p>申请创建时必须基于同一电梯一周内达到阈值的困人事件，并在事务内重新核验、
 * 固化触发事件 / 故障代码 / 投诉汇总快照；快照一旦写入不可更改。</p>
 *
 * <p>方案内容不落本表：每次提交生成一条 {@link RectificationPlanVersion}（版本递增），
 * 每次复检生成一条不可变 RecheckRecord，历史失败版本及其字段永久可查。</p>
 */
@Data
@Entity
@Table(name = "rectification_plans")
public class RectificationPlan {

    public enum PlanStatus {
        REQUESTED,          // 已申请，待维保提交首版方案
        SUBMITTED,          // 最新版方案已提交，待复检
        RECHECK_FAILED,     // 最新版复检未通过（继续停梯，待提交修订版本）
        RECHECK_PASSED      // 最新版复检通过（闭环，电梯恢复运行）
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "elevator_id")
    private Elevator elevator;

    /** 触发的困人事件（可空，冗余便于展示） */
    private Long eventId;

    /** 维保单位名称（冗余，便于展示） */
    private String companyName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanStatus status = PlanStatus.REQUESTED;

    // ---------- 物业申请 ----------

    private String requestedBy;

    private LocalDateTime requestedAt = LocalDateTime.now();

    @Column(length = 1000)
    private String requestNote;

    // ---------- 触发证据快照（申请时事务内核验并固化，此后不可变） ----------

    /** 申请时生效的触发阈值（同一电梯窗口期内困人事件起数） */
    private Integer triggerThreshold;

    /** 证据核验窗口起点（申请时刻前 N 天） */
    private LocalDateTime evidenceWindowStart;

    /** 证据核验窗口终点（申请时刻） */
    private LocalDateTime evidenceWindowEnd;

    /** 窗口内困人事件起数（必须 >= triggerThreshold 才允许申请） */
    private Integer triggerEventCount;

    /** 触发事件快照 JSON：[{id, eventNo, faultCode, alarmTime, status}] */
    @Column(length = 4000)
    private String triggerEventsJson;

    /** 故障代码快照 JSON：["E21-安全回路断开", ...] */
    @Column(length = 2000)
    private String faultCodesJson;

    /** 投诉汇总快照 JSON：{windowDays, count, items:[{id, ownerName, content, status, createdAt}]} */
    @Column(length = 4000)
    private String complaintSummaryJson;

    // ---------- 版本推进 ----------

    /** 当前（最新）方案版本号；0 表示尚未提交任何版本 */
    private Integer currentVersionNo = 0;
}
