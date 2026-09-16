package com.elevator.rescue.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 续办关怀与新停梯批次的关联（一条帮扶可被依次纳入多个后续批次）。
 *
 * <p>新整改单创建时，本电梯所有「续办关怀」记录自动登记一条关联，
 * 使下一次停梯只汇总「新批次需求 + 明确续办的需求」，且历史可审计。</p>
 */
@Data
@Entity
@Table(name = "assistance_plan_links",
        uniqueConstraints = @UniqueConstraint(columnNames = {"assistance_id", "rectification_plan_id"}))
public class AssistancePlanLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "assistance_id")
    private ElderlyAssistance assistance;

    /** 纳入的整改单（停梯批次） */
    @Column(name = "rectification_plan_id", nullable = false)
    private Long rectificationPlanId;

    private LocalDateTime linkedAt = LocalDateTime.now();
}
