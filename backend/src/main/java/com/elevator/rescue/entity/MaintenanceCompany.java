package com.elevator.rescue.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "maintenance_companies")
public class MaintenanceCompany {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    private String contactPerson;

    private String contactPhone;

    /** 24 小时应急救援电话 */
    private String emergencyPhone;

    /** 信用分（满分 100，考核扣分） */
    private Integer creditScore = 100;

    /** 处罚次数 */
    private Integer penaltyCount = 0;

    @Column(length = 1000)
    private String remark;
}
