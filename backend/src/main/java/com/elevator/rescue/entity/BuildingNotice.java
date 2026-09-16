package com.elevator.rescue.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 楼栋公告（停梯公告 / 备用梯开放 / 老人临时协助 / 复检通知等）。
 *
 * <p>与停梯整改单联动的公告（{@link #rectificationPlanId} 非空）由整改流程统一管理：
 * 申请停梯整改时发布停梯公告；最新方案版本复检通过的同一处置中，关联停梯公告被撤回
 * （保留可审计）并发布唯一有效的恢复公告。撤回记录保留撤回时间与原因。</p>
 */
@Data
@Entity
@Table(name = "building_notices")
public class BuildingNotice {

    public enum NoticeType {
        STOP_NOTICE,    // 停梯公告
        BACKUP_LIFT,    // 备用梯开放
        ELDERLY_ASSIST, // 老人上下楼临时协助
        RECHECK,        // 复检通知
        GENERAL         // 一般通知
    }

    public enum NoticeStatus {
        PUBLISHED,
        REVOKED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "building_id")
    private Building building;

    /** 关联的困人事件（可空） */
    private Long eventId;

    /** 关联的停梯整改单（可空；停梯公告/恢复公告由整改流程联动维护） */
    private Long rectificationPlanId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private NoticeType type;

    private String title;

    @Column(length = 2000)
    private String content;

    private String publisherName;

    private LocalDateTime publishedAt = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private NoticeStatus status = NoticeStatus.PUBLISHED;

    /** 撤回时间（可空，撤回时写入，历史公告可审计） */
    private LocalDateTime revokedAt;

    /** 撤回原因（可空） */
    @Column(length = 500)
    private String revokeReason;
}
