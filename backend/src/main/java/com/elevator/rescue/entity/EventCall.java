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

    // ---------- 乘客健康安抚信息 ----------

    /** 乘客年龄（约数或描述，如“约 65 岁”“儿童 5 岁”） */
    private String passengerAge;

    /** 乘客是否恐慌 */
    private Boolean panic = false;

    /** 是否有心脏病乘客 */
    private Boolean heartDisease = false;

    /** 是否有孕妇 */
    private Boolean pregnant = false;

    /** 乘客能否清楚描述状态（false → 提示保持通话并让保安现场确认） */
    private Boolean stateClear = true;
}
