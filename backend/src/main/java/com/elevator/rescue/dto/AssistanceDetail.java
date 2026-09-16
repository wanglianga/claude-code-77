package com.elevator.rescue.dto;

import com.elevator.rescue.entity.AssistanceEvent;
import com.elevator.rescue.entity.AssistancePlanLink;
import com.elevator.rescue.entity.ElderlyAssistance;

import java.util.List;

/**
 * 帮扶记录详情：记录本体 + 处理留痕时间线 + 历次纳入的停梯批次（追溯）。
 */
public record AssistanceDetail(ElderlyAssistance assistance,
                               List<AssistanceEvent> events,
                               List<AssistancePlanLink> batchLinks) {
}
