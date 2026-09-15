package com.elevator.rescue.entity;

import com.elevator.rescue.entity.User.Role;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 事件通知记录（维保 / 保安 / 楼栋管家 / 消防）
 */
@Data
@Entity
@Table(name = "event_notifications")
public class EventNotification {

    public enum NotifyStatus {
        SENT,           // 已通知
        ACKED,          // 已确认
        UNREACHABLE     // 联系不上
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "event_id")
    private RescueEvent event;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Role targetRole;

    private String targetName;

    private String targetPhone;

    private LocalDateTime notifiedAt;

    private LocalDateTime acknowledgedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private NotifyStatus status = NotifyStatus.SENT;

    @Column(length = 500)
    private String note;
}
