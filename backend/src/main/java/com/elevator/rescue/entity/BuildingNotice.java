package com.elevator.rescue.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 楼栋公告（停梯公告 / 备用梯开放 / 老人临时协助 / 复检通知等）
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
}
