package com.elevator.rescue.dto;

import com.elevator.rescue.entity.RecheckRecord;
import com.elevator.rescue.entity.RectificationPlan;
import com.elevator.rescue.entity.RectificationPlanVersion;

import java.util.List;

/**
 * 整改申请详情：申请单（含触发证据快照）+ 全部方案版本（含历史失败版本）+ 不可变复检记录。
 */
public record PlanDetail(RectificationPlan plan,
                         List<RectificationPlanVersion> versions,
                         List<RecheckRecord> recheckRecords) {
}
