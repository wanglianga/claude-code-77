package com.elevator.rescue.dto;

import com.elevator.rescue.entity.ElderlyAssistance;
import com.elevator.rescue.entity.RectificationPlan;

import java.util.List;

/**
 * 一个进行中停梯批次的帮扶汇总：本批次新登记需求 + 历史批次明确续办转入的关怀。
 * 人力安排只统计这两类，已恢复电梯的旧批次记录不再计入。
 */
public record CurrentBatchView(RectificationPlan plan,
                               List<ElderlyAssistance> newBatch,
                               List<ElderlyAssistance> carried) {
}
