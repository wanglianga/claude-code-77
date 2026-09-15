package com.elevator.rescue.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "buildings")
public class Building {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    private Integer floors;

    private Integer units;

    private String address;

    /** 楼栋负责人（管家） */
    private String managerName;

    private String managerPhone;
}
