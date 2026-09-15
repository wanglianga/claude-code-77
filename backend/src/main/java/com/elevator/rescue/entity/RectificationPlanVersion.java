package com.elevator.rescue.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 整改方案版本：每次提交生成一个新版本行（versionNo 从 1 递增）。
 *
 * <p>版本行提交后内容不可修改；复检未通过时不得覆盖本行及其失败结论，
 * 只能在所属申请下提交新的修订版本。每版的配件、预计到货、复检人、
 * 公告时间、提交/复检人员与结论均永久保留、可追溯。</p>
 */
@Data
@Entity
@Table(name = "rectification_plan_versions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"plan_id", "version_no"}))
public class RectificationPlanVersion {

    public enum VersionStatus {
        SUBMITTED,          // 已提交，待复检
        RECHECK_PASSED,     // 复检通过
        RECHECK_FAILED      // 复检未通过
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "plan_id")
    private RectificationPlan plan;

    /** 版本号，同一申请内从 1 开始递增 */
    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VersionStatus status = VersionStatus.SUBMITTED;

    // ---------- 维保提交（提交后不可变） ----------

    /** 需更换的配件 */
    @Column(length = 1000)
    private String parts;

    /** 配件预计到货日期 */
    private LocalDate expectedArrival;

    /** 复检人 */
    private String recheckInspector;

    /** 业主公告发布时间 */
    private LocalDateTime noticePublishTime;

    @Column(length = 2000)
    private String planDetail;

    private String submittedBy;

    private LocalDateTime submittedAt;

    // ---------- 复检结论（复检登记时写入一次，此后不可变） ----------

    private LocalDateTime recheckedAt;

    @Column(length = 500)
    private String recheckResult;

    private String recheckedBy;
}
