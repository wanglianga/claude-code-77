package com.elevator.rescue.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "part_replacements")
public class PartReplacement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "elevator_id")
    private Elevator elevator;

    /** 关联的困人事件（可空） */
    private Long eventId;

    private String partName;

    private LocalDate replaceDate;

    @Column(length = 1000)
    private String reason;

    private BigDecimal cost;

    /** 更换人/单位 */
    private String replacedBy;
}
