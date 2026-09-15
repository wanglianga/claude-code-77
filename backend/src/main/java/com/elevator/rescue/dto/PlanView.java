package com.elevator.rescue.dto;

import com.elevator.rescue.entity.RectificationPlan;
import com.elevator.rescue.entity.RectificationPlanVersion;

/**
 * 整改申请列表视图：申请单 + 最新方案版本 + 版本总数。
 */
public record PlanView(RectificationPlan plan,
                       RectificationPlanVersion latestVersion,
                       int versionCount) {
}
