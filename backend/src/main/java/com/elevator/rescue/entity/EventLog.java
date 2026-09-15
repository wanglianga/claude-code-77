package com.elevator.rescue.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 事件处置时间线
 */
@Data
@Entity
@Table(name = "event_logs")
public class EventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "event_id")
    private RescueEvent event;

    private String actorName;

    private String action;

    @Column(length = 1000)
    private String detail;

    private LocalDateTime createdAt = LocalDateTime.now();
}
