package com.elevator.rescue.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 复检记录：每次复检登记生成一条，只增不改（不可变审计记录）。
 *
 * <p>与方案版本一一对应（每个版本只允许复检登记一次），
 * 即使后续产生新的修订版本，历史版本的复检记录也永久保留。</p>
 */
@Data
@Entity
@Table(name = "recheck_records")
public class RecheckRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "version_id")
    private RectificationPlanVersion version;

    /** 冗余：所属整改申请 id，便于按申请查询全部复检记录 */
    @Column(nullable = false)
    private Long planId;

    /** 复检是否通过 */
    @Column(nullable = false)
    private Boolean pass;

    /** 复检结果说明 */
    @Column(nullable = false, length = 500)
    private String result;

    /** 复检登记人 */
    @Column(nullable = false)
    private String recheckedBy;

    /** 复检登记时间 */
    @Column(nullable = false)
    private LocalDateTime recheckedAt = LocalDateTime.now();
}
