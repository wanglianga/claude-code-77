package com.elevator.rescue.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业主投诉
 */
@Data
@Entity
@Table(name = "owner_complaints")
public class OwnerComplaint {

    public enum ComplaintStatus {
        PENDING,        // 待处理
        PROCESSING,     // 处理中
        RESOLVED        // 已办结
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "elevator_id")
    private Elevator elevator;

    /** 关联的困人事件（可空） */
    private Long eventId;

    private String ownerName;

    private String ownerPhone;

    private String buildingName;

    @Column(length = 1000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ComplaintStatus status = ComplaintStatus.PENDING;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime handledAt;

    private String handlerName;

    @Column(length = 1000)
    private String result;
}
