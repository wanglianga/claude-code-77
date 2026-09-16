package com.elevator.rescue.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 停梯期间老人上下楼需求与临时帮扶登记。
 *
 * <p>每条帮扶记录关联发起它的整改单（停梯批次 {@link #rectificationPlan}）：
 * 电梯恢复运行时，本批次未办结记录由系统自动转入「待复核」，不再计入
 * 「停梯期间帮扶中」；管家逐条确认「已完成 / 继续关怀 / 改约」并保留处理人与时间。
 * 「续办关怀」记录在下一次停梯（新整改单）时自动纳入新批次汇总，
 * 历史批次与电梯、公告、复检记录可互相追溯。</p>
 */
@Data
@Entity
@Table(name = "elderly_assistances")
public class ElderlyAssistance {

    public enum AssistanceStatus {
        ACTIVE,         // 帮扶中（本批次停梯期间）
        PENDING_REVIEW, // 待复核（电梯已恢复，待管家确认结案或续办）
        CONTINUED,      // 续办关怀（恢复后仍持续关怀，下次停梯自动纳入新批次）
        RESCHEDULED,    // 已改约（服务改期，本批次结案）
        RESOLVED        // 已完成（已办结）
    }

    /** 管家复核动作 */
    public enum ReviewAction {
        COMPLETE,   // 确认完成（办结）
        CONTINUE,   // 继续关怀（转为续办）
        RESCHEDULE  // 改约（约定下次服务时间，本批次结案）
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "building_id")
    private Building building;

    /** 关联的停梯电梯 */
    @ManyToOne
    @JoinColumn(name = "elevator_id")
    private Elevator elevator;

    /** 发起本帮扶的整改单（停梯批次）；恢复运行、批次汇总均以此为界 */
    @ManyToOne
    @JoinColumn(name = "rectification_plan_id")
    private RectificationPlan rectificationPlan;

    /** 老人/住户姓名 */
    private String residentName;

    private String roomNo;

    private String phone;

    /** 上下楼需求（就医、买菜、透析、接送等） */
    @Column(length = 500)
    private String needDescription;

    /** 临时帮扶人员（人力安排） */
    private String helperName;

    private String helperPhone;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AssistanceStatus status = AssistanceStatus.ACTIVE;

    /** 登记人 */
    private String createdByName;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime resolvedAt;

    // ---------- 管家复核留痕 ----------

    /** 复核处理人 */
    private String reviewedBy;

    /** 复核处理时间 */
    private LocalDateTime reviewedAt;

    /** 复核动作（完成 / 续办 / 改约） */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ReviewAction reviewAction;

    /** 复核备注 */
    @Column(length = 500)
    private String reviewNote;

    /** 改约的下次服务时间（仅改约时填写） */
    private LocalDateTime nextAppointmentAt;
}
