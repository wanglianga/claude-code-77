package com.elevator.rescue.dto;

import com.elevator.rescue.entity.RescueEvent.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 事件处置相关的请求 DTO 集合
 */
public class EventDtos {

    public record EventCreateRequest(
            @NotNull(message = "请选择电梯") Long elevatorId,
            @NotNull(message = "请选择报警来源") AlarmSource alarmSource,
            LocalDateTime alarmTime,
            String trappedFloor,
            @NotNull(message = "请填写轿厢人数") Integer passengerCount,
            Integer elderlyCount,
            Integer childrenCount,
            CallStatus callStatus,
            String doorZone,
            String reporterName,
            String reporterPhone,
            String reportDetail) {
    }

    public record DispatchRequest(
            Boolean notifyMaintenance,
            Boolean notifySecurity,
            Boolean notifyButler,
            Boolean notifyFire,
            String note) {
    }

    public record CallRequest(
            LocalDateTime callTime,
            String passengerState,
            @NotBlank(message = "请填写通话内容") String content,
            CallStatus callStatus,
            String passengerAge,
            Boolean panic,
            Boolean heartDisease,
            Boolean pregnant,
            Boolean stateClear) {
    }

    public record ArriveRequest(
            @NotNull(message = "请选择到场方") String party,
            LocalDateTime arrivedAt,
            String note) {
    }

    public record ReleaseRequest(
            LocalDateTime releasedAt,
            @NotBlank(message = "请填写开门方式") String doorOpenMethod,
            @NotBlank(message = "请填写故障代码") String faultCode,
            @NotBlank(message = "请填写乘客身体状态") String passengerHealth,
            @NotNull(message = "请明确是否需要医疗协助") Boolean medicalAssistance,
            String rescueNote) {
    }

    public record ResetRequest(
            LocalDateTime resetAt,
            Boolean elevatorStopped,
            LocalDateTime recheckedAt,
            @NotBlank(message = "请填写复检结果") String recheckResult) {
    }

    public record CloseRequest(
            @NotNull(message = "请选择责任判定") Responsibility responsibility,
            @NotBlank(message = "请填写责任判定说明") String responsibilityDetail,
            @NotNull(message = "请选择费用承担方") CostBearer costBearer,
            BigDecimal costAmount,
            String rectification,
            LocalDate rectificationDeadline,
            Boolean ownerNotified,
            String closeRemark) {
    }

    public record FlagsRequest(
            Boolean maintenanceLate,
            Boolean propertyUnreachable,
            Boolean fireArrivedFirst,
            Boolean compensationRequested,
            Boolean repeatFault,
            Boolean misuseClaimed,
            String compensationDetail) {
    }

    public record NotificationUpdateRequest(
            @NotNull(message = "请选择通知状态") String status) {
    }

    public record FollowupRequest(
            @NotBlank(message = "请填写回访对象") String ownerName,
            String ownerPhone,
            LocalDateTime followupTime,
            Integer satisfaction,
            String feedback) {
    }

    public record RectificationRequest(
            @NotNull Boolean done) {
    }
}
