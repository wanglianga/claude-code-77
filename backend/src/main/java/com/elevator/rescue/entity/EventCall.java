package com.elevator.rescue.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 与轿厢内乘客的通话安抚记录
 */
@Data
@Entity
@Table(name = "event_calls")
public class EventCall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "event_id")
    private RescueEvent event;

    @ManyToOne
    @JoinColumn(name = "caller_id")
    private User caller;

    private LocalDateTime callTime;

    /** 乘客情绪/身体状态 */
    private String passengerState;

    @Column(length = 1000)
    private String content;
}
