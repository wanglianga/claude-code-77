package com.elevator.rescue.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 困人救援事件 —— 平台核心实体。
 * 报警、调度、到场、释放、复位、停梯、复检、责任判定、费用、整改、业主通知
 * 全部归集在同一事件内闭环处理。
 */
@Data
@Entity
@Table(name = "rescue_events")
public class RescueEvent {

    public enum AlarmSource {
        IOT,        // 电梯物联网报警
        PHONE,      // 乘客电话求助
        PATROL      // 物业巡查发现
    }

    public enum CallStatus {
        SMOOTH,         // 通话顺畅
        INTERMITTENT,   // 时断时续
        LOST            // 无法接通
    }

    public enum EventStatus {
        PENDING,        // 已接警，待调度
        DISPATCHED,     // 已通知各方
        ARRIVED,        // 维保已到场
        RELEASED,       // 困人已释放
        RESET,          // 已复位/复检
        CLOSED          // 事件关闭（归档）
    }

    public enum Responsibility {
        MAINTENANCE,        // 维保单位责任
        PROPERTY,           // 物业管理责任
        OWNER_MISUSE,       // 业主使用不当
        EQUIPMENT_AGING,    // 设备老化
        JOINT,              // 共同责任
        PENDING             // 待定
    }

    public enum CostBearer {
        MAINTENANCE,    // 维保单位承担
        PROPERTY,       // 物业承担
        OWNER,          // 业主承担
        SHARED,         // 共同分担
        NONE            // 无费用
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String eventNo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "elevator_id")
    private Elevator elevator;

    @ManyToOne(optional = false)
    @JoinColumn(name = "building_id")
    private Building building;

    // ---------- 接警信息 ----------
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlarmSource alarmSource;

    private LocalDateTime alarmTime;

    /** 被困楼层 */
    private String trappedFloor;

    /** 轿厢人数 */
    private Integer passengerCount;

    private Integer elderlyCount = 0;

    private Integer childrenCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CallStatus callStatus;

    /** 门区位置（默认取电梯档案，可修改） */
    private String doorZone;

    private String reporterName;

    private String reporterPhone;

    @Column(length = 1000)
    private String reportDetail;

    @ManyToOne
    @JoinColumn(name = "handler_id")
    private User handler;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventStatus status = EventStatus.PENDING;

    // ---------- 通知调度 ----------
    private LocalDateTime maintenanceNotifiedAt;
    private LocalDateTime securityNotifiedAt;
    private LocalDateTime butlerNotifiedAt;
    private LocalDateTime fireNotifiedAt;
    private LocalDateTime maintenanceArrivedAt;
    private LocalDateTime fireArrivedAt;

    // ---------- 救援释放 ----------
    private LocalDateTime releasedAt;

    /** 开门方式：松闸盘车/检修运行/消防破拆 等 */
    private String doorOpenMethod;

    private String faultCode;

    @Column(length = 500)
    private String passengerHealth;

    private Boolean medicalAssistance = false;

    @Column(length = 1000)
    private String rescueNote;

    // ---------- 异常标记 ----------
    private Boolean maintenanceLate = false;        // 维保迟到
    private Boolean propertyUnreachable = false;    // 物业联系不上
    private Boolean fireArrivedFirst = false;       // 消防先到场
    private Boolean compensationRequested = false;  // 乘客要求赔偿
    private Boolean repeatFault = false;            // 同一电梯反复故障
    private Boolean misuseClaimed = false;          // 维保认为使用不当

    @Column(length = 1000)
    private String compensationDetail;

    // ---------- 复位 / 停梯 / 复检 ----------
    private LocalDateTime resetAt;

    private Boolean elevatorStopped = false;

    private LocalDateTime recheckedAt;

    @Column(length = 500)
    private String recheckResult;

    // ---------- 关闭归档 ----------
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Responsibility responsibility;

    @Column(length = 1000)
    private String responsibilityDetail;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CostBearer costBearer;

    private BigDecimal costAmount;

    @Column(length = 1000)
    private String rectification;

    private LocalDate rectificationDeadline;

    private Boolean rectificationDone = false;

    private Boolean ownerNotified = false;

    private LocalDateTime closedAt;

    @Column(length = 1000)
    private String closeRemark;

    private LocalDateTime createdAt = LocalDateTime.now();
}
