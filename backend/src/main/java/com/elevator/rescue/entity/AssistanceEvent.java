package com.elevator.rescue.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 帮扶记录处理留痕（只增不改）。
 *
 * <p>登记、电梯恢复自动转待复核、管家复核（完成/续办/改约）、
 * 续办纳入新停梯批次，每一步都记录处理人与时间，供追溯。</p>
 */
@Data
@Entity
@Table(name = "assistance_events")
public class AssistanceEvent {

    public enum Action {
        CREATE,              // 登记
        AUTO_PENDING_REVIEW, // 电梯恢复运行，系统自动转待复核
        COMPLETE,            // 管家确认完成（办结）
        CONTINUE,            // 管家确认继续关怀（转续办）
        RESCHEDULE,          // 管家确认改约
        CARRY_IN             // 续办关怀纳入新停梯批次
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "assistance_id")
    private ElderlyAssistance assistance;

    /** 处理人姓名；系统自动动作（如恢复联动）为 null */
    private String actorName;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private Action action;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ElderlyAssistance.AssistanceStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ElderlyAssistance.AssistanceStatus toStatus;

    @Column(length = 500)
    private String note;

    private LocalDateTime createdAt = LocalDateTime.now();
}
