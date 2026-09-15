package com.elevator.rescue.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 事件关闭后的业主回访
 */
@Data
@Entity
@Table(name = "owner_followups")
public class OwnerFollowup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "event_id")
    private RescueEvent event;

    private String ownerName;

    private String ownerPhone;

    private LocalDateTime followupTime;

    /** 满意度 1-5 */
    private Integer satisfaction;

    @Column(length = 1000)
    private String feedback;

    private String followerName;
}
