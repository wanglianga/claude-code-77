package com.elevator.rescue.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 停梯期间老人上下楼需求与临时帮扶登记
 */
@Data
@Entity
@Table(name = "elderly_assistances")
public class ElderlyAssistance {

    public enum AssistanceStatus {
        ACTIVE,     // 帮扶中
        RESOLVED    // 已解决
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "building_id")
    private Building building;

    /** 关联的停梯电梯（可空） */
    @ManyToOne
    @JoinColumn(name = "elevator_id")
    private Elevator elevator;

    /** 老人/住户姓名 */
    private String residentName;

    private String roomNo;

    private String phone;

    /** 上下楼需求（就医、买菜、接送等） */
    @Column(length = 500)
    private String needDescription;

    /** 临时帮扶人员 */
    private String helperName;

    private String helperPhone;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AssistanceStatus status = AssistanceStatus.ACTIVE;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime resolvedAt;
}
