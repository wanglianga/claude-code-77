package com.elevator.rescue.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sys_users")
public class User {

    public enum Role {
        ADMIN,          // 系统管理员
        DUTY,           // 物业值班员
        MAINTENANCE,    // 维保人员
        SECURITY,       // 保安
        BUTLER,         // 楼栋管家
        FIRE,           // 消防救援联络
        OWNER           // 业主
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 50)
    private String realName;

    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    /** 维保人员所属维保单位 */
    private Long companyId;

    /** 楼栋管家负责楼栋 */
    private Long buildingId;

    private Boolean enabled = true;

    private LocalDateTime createdAt = LocalDateTime.now();
}
