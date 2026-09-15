package com.elevator.rescue.service;

import com.elevator.rescue.dto.EventDtos.*;
import com.elevator.rescue.entity.*;
import com.elevator.rescue.entity.RescueEvent.EventStatus;
import com.elevator.rescue.entity.User.Role;
import com.elevator.rescue.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class EventService {

    private final RescueEventRepository eventRepo;
    private final EventLogRepository logRepo;
    private final EventCallRepository callRepo;
    private final EventNotificationRepository notificationRepo;
    private final ElevatorRepository elevatorRepo;
    private final OwnerFollowupRepository followupRepo;
    private final BuildingNoticeRepository noticeRepo;
    private final OwnerComplaintRepository complaintRepo;
    private final PartReplacementRepository partRepo;
    private final MaintenanceCompanyRepository companyRepo;
    private final UserRepository userRepo;

    @Value("${app.arrive-limit-minutes:30}")
    private int arriveLimitMinutes;

    /** 救援超时提醒阈值（分钟）：超过仍未释放 → 提醒联系 120 / 消防优先介入 */
    @Value("${app.rescue-alert-minutes:30}")
    private int rescueAlertMinutes;

    /** 通话间隔提醒阈值（分钟）：超过未与乘客通话 → 提醒保持通话频率 */
    @Value("${app.call-remind-minutes:10}")
    private int callRemindMinutes;

    public RescueEvent get(Long id) {
        return eventRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("事件不存在: " + id));
    }

    // ==================== 权限与资料门禁 ====================

    /**
     * 资料门禁（读）：维保人员仅见本维保单位事件，楼栋管家/业主仅见本楼栋事件。
     */
    public void checkReadScope(User user, RescueEvent e) {
        switch (user.getRole()) {
            case MAINTENANCE -> {
                Long companyId = e.getElevator().getCompany() != null ? e.getElevator().getCompany().getId() : null;
                if (user.getCompanyId() == null || !user.getCompanyId().equals(companyId)) {
                    throw new AccessDeniedException("无权查看非本维保单位负责的事件资料");
                }
            }
            case BUTLER, OWNER -> {
                if (user.getBuildingId() == null || !user.getBuildingId().equals(e.getBuilding().getId())) {
                    throw new AccessDeniedException("无权查看非本楼栋的事件资料");
                }
            }
            default -> {
                // ADMIN / DUTY / SECURITY / FIRE 可查看全部
            }
        }
    }

    /** 角色门禁：不在允许角色内则拒绝（业主、保安等不得推进救援状态） */
    private void requireRole(User user, String action, Role... roles) {
        for (Role role : roles) {
            if (user.getRole() == role) {
                return;
            }
        }
        throw new AccessDeniedException("当前角色（" + user.getRole() + "）无权" + action);
    }

    /** 维保单位范围：维保人员只能处置本单位负责的电梯事件 */
    private void requireMaintenanceScope(User user, RescueEvent e) {
        if (user.getRole() != Role.MAINTENANCE) {
            return;
        }
        Long companyId = e.getElevator().getCompany() != null ? e.getElevator().getCompany().getId() : null;
        if (user.getCompanyId() == null || !user.getCompanyId().equals(companyId)) {
            throw new AccessDeniedException("维保人员只能处置本维保单位负责的电梯事件");
        }
    }

    /** 楼栋范围：管家只能处置本楼栋事件（无关管家不得推进救援状态） */
    private void requireButlerScope(User user, RescueEvent e) {
        if (user.getRole() != Role.BUTLER) {
            return;
        }
        if (user.getBuildingId() == null || !user.getBuildingId().equals(e.getBuilding().getId())) {
            throw new AccessDeniedException("楼栋管家只能处置本楼栋的电梯事件");
        }
    }

    // ---------------- 接警登记 ----------------

    @Transactional
    public RescueEvent create(EventCreateRequest req, User handler) {
        requireRole(handler, "接警登记", Role.ADMIN, Role.DUTY);

        Elevator elevator = elevatorRepo.findById(req.elevatorId())
                .orElseThrow(() -> new IllegalArgumentException("电梯不存在"));

        RescueEvent e = new RescueEvent();
        e.setEventNo(nextEventNo());
        e.setElevator(elevator);
        e.setBuilding(elevator.getBuilding());
        e.setAlarmSource(req.alarmSource());
        e.setAlarmTime(req.alarmTime() != null ? req.alarmTime() : LocalDateTime.now());
        e.setTrappedFloor(req.trappedFloor());
        e.setPassengerCount(req.passengerCount());
        e.setElderlyCount(req.elderlyCount() != null ? req.elderlyCount() : 0);
        e.setChildrenCount(req.childrenCount() != null ? req.childrenCount() : 0);
        e.setCallStatus(req.callStatus());
        e.setDoorZone(req.doorZone() != null && !req.doorZone().isBlank() ? req.doorZone() : elevator.getDoorZone());
        e.setReporterName(req.reporterName());
        e.setReporterPhone(req.reporterPhone());
        e.setReportDetail(req.reportDetail());
        e.setHandler(handler);
        e.setStatus(EventStatus.PENDING);

        // 同一电梯 90 天内已有 >=2 次困人事件 → 标记反复故障
        long recent = eventRepo.countByElevatorIdAndAlarmTimeAfter(
                elevator.getId(), LocalDateTime.now().minusDays(90));
        e.setRepeatFault(recent >= 2);

        eventRepo.save(e);
        log(e, handler.getRealName(), "接警登记",
                "报警来源: " + alarmSourceLabel(e.getAlarmSource())
                        + "，被困 " + e.getPassengerCount() + " 人"
                        + (e.getElderlyCount() > 0 ? "（含老人 " + e.getElderlyCount() + " 人）" : "")
                        + (e.getChildrenCount() > 0 ? "（含儿童 " + e.getChildrenCount() + " 人）" : "")
                        + (Boolean.TRUE.equals(e.getRepeatFault()) ? "；该电梯 90 天内多次困人，已标记反复故障" : ""));
        return e;
    }

    // ---------------- 通知调度 ----------------

    @Transactional
    public RescueEvent dispatch(Long id, DispatchRequest req, User operator) {
        requireRole(operator, "通知调度", Role.ADMIN, Role.DUTY);
        RescueEvent e = get(id);
        requireStatus(e, EventStatus.PENDING);

        // 调度完整性：维保、保安、楼栋管家为必通知方
        if (!Boolean.TRUE.equals(req.notifyMaintenance())
                || !Boolean.TRUE.equals(req.notifySecurity())
                || !Boolean.TRUE.equals(req.notifyButler())) {
            throw new IllegalArgumentException("调度必须同步通知维保单位、保安和楼栋管家");
        }
        // 高风险场景（老人儿童被困、通话中断）必须通知消防救援
        if (isHighRisk(e) && !Boolean.TRUE.equals(req.notifyFire())) {
            throw new IllegalArgumentException("存在老人儿童被困或通话中断等高风险情形，必须同步通知消防救援");
        }

        LocalDateTime now = LocalDateTime.now();
        List<String> notified = new ArrayList<>();

        MaintenanceCompany company = e.getElevator().getCompany();
        createNotification(e, Role.MAINTENANCE,
                company != null ? company.getContactPerson() : "维保单位",
                company != null ? company.getEmergencyPhone() : null, req.note(), now);
        e.setMaintenanceNotifiedAt(now);
        notified.add("维保单位" + (company != null ? "（" + company.getName() + "）" : ""));

        User security = userRepo.findByRole(Role.SECURITY).stream().findFirst().orElse(null);
        createNotification(e, Role.SECURITY,
                security != null ? security.getRealName() : "保安",
                security != null ? security.getPhone() : null, req.note(), now);
        e.setSecurityNotifiedAt(now);
        notified.add("保安");

        User butler = userRepo.findByRoleAndBuildingId(Role.BUTLER, e.getBuilding().getId())
                .stream().findFirst().orElse(null);
        createNotification(e, Role.BUTLER,
                butler != null ? butler.getRealName() : e.getBuilding().getManagerName(),
                butler != null ? butler.getPhone() : e.getBuilding().getManagerPhone(), req.note(), now);
        e.setButlerNotifiedAt(now);
        notified.add("楼栋管家");

        if (Boolean.TRUE.equals(req.notifyFire())) {
            User fire = userRepo.findByRole(Role.FIRE).stream().findFirst().orElse(null);
            createNotification(e, Role.FIRE,
                    fire != null ? fire.getRealName() : "消防救援",
                    fire != null ? fire.getPhone() : null, req.note(), now);
            e.setFireNotifiedAt(now);
            notified.add("消防救援");
        }

        e.setStatus(EventStatus.DISPATCHED);
        log(e, operator.getRealName(), "通知调度", "已同步通知: " + String.join("、", notified)
                + (isHighRisk(e) ? "（高风险场景，已联动消防）" : ""));
        return e;
    }

    private boolean isHighRisk(RescueEvent e) {
        // 高风险场景：老人/儿童被困，或通话中断（无法接通、时断时续）
        return (e.getElderlyCount() != null && e.getElderlyCount() > 0)
                || (e.getChildrenCount() != null && e.getChildrenCount() > 0)
                || e.getCallStatus() == RescueEvent.CallStatus.LOST
                || e.getCallStatus() == RescueEvent.CallStatus.INTERMITTENT;
    }

    // ---------------- 通话安抚 ----------------

    @Transactional
    public RescueEvent addCall(Long id, CallRequest req, User caller) {
        requireRole(caller, "记录通话安抚", Role.ADMIN, Role.DUTY, Role.MAINTENANCE, Role.BUTLER, Role.FIRE);
        RescueEvent e = get(id);
        requireMaintenanceScope(caller, e);
        requireButlerScope(caller, e);
        if (e.getStatus() == EventStatus.CLOSED) {
            throw new IllegalStateException("事件已关闭，不能再添加通话记录");
        }
        EventCall call = new EventCall();
        call.setEvent(e);
        call.setCaller(caller);
        call.setCallTime(req.callTime() != null ? req.callTime() : LocalDateTime.now());
        call.setPassengerState(req.passengerState());
        call.setContent(req.content());
        // 乘客健康安抚信息
        call.setPassengerAge(req.passengerAge());
        call.setPanic(Boolean.TRUE.equals(req.panic()));
        call.setHeartDisease(Boolean.TRUE.equals(req.heartDisease()));
        call.setPregnant(Boolean.TRUE.equals(req.pregnant()));
        call.setStateClear(req.stateClear() == null || req.stateClear());
        callRepo.save(call);

        if (req.callStatus() != null) {
            e.setCallStatus(req.callStatus());
        }

        StringBuilder health = new StringBuilder();
        if (Boolean.TRUE.equals(call.getPanic())) {
            health.append("、恐慌");
        }
        if (Boolean.TRUE.equals(call.getHeartDisease())) {
            health.append("、心脏病");
        }
        if (Boolean.TRUE.equals(call.getPregnant())) {
            health.append("、孕妇");
        }
        log(e, caller.getRealName(), "通话安抚",
                "与轿厢乘客通话，乘客状态: " + (req.passengerState() != null ? req.passengerState() : "未记录")
                        + (health.length() > 0 ? "；健康风险: " + health.substring(1) : "")
                        + (Boolean.FALSE.equals(call.getStateClear()) ? "；乘客无法清楚描述状态" : ""));

        // 乘客无法清楚描述状态 → 自动提示值班员保持通话，并通知保安现场确认（每事件一次）
        if (Boolean.FALSE.equals(call.getStateClear())) {
            autoNotifySecurityOnce(e,
                    "【自动提醒】乘客无法清楚描述状态：值班员请保持通话，请保安到现场确认轿厢声音和楼层");
        }
        // 通话中断 → 自动提示物业重拨，并通知保安确认轿厢内回应（每事件一次）
        if (e.getCallStatus() == RescueEvent.CallStatus.LOST) {
            autoNotifySecurityOnce(e,
                    "【自动提醒】通话中断：物业将立即再次拨打轿厢通话，请现场保安确认轿厢内回应");
        }
        return e;
    }

    /** 自动向保安推送一次现场处置提醒（同一事件同一提醒不重复） */
    private void autoNotifySecurityOnce(RescueEvent e, String note) {
        boolean exists = notificationRepo.findByEventIdOrderByNotifiedAtAsc(e.getId()).stream()
                .anyMatch(n -> note.equals(n.getNote()));
        if (exists) {
            return;
        }
        User security = userRepo.findByRole(Role.SECURITY).stream().findFirst().orElse(null);
        EventNotification n = new EventNotification();
        n.setEvent(e);
        n.setTargetRole(Role.SECURITY);
        n.setTargetName(security != null ? security.getRealName() : "保安");
        n.setTargetPhone(security != null ? security.getPhone() : null);
        n.setNotifiedAt(LocalDateTime.now());
        n.setNote(note);
        notificationRepo.save(n);
        log(e, "系统", "自动提醒", note);
    }

    // ---------------- 到场登记 ----------------

    @Transactional
    public RescueEvent arrive(Long id, ArriveRequest req, User operator) {
        RescueEvent e = get(id);
        boolean fireParty = "FIRE".equalsIgnoreCase(req.party());
        if (fireParty) {
            requireRole(operator, "登记消防到场", Role.ADMIN, Role.DUTY, Role.FIRE);
        } else {
            requireRole(operator, "登记维保到场", Role.ADMIN, Role.DUTY, Role.MAINTENANCE);
            requireMaintenanceScope(operator, e);
        }
        if (e.getStatus() != EventStatus.DISPATCHED && e.getStatus() != EventStatus.ARRIVED) {
            throw new IllegalStateException("当前状态不允许到场登记（需先完成通知调度）");
        }
        LocalDateTime arrivedAt = req.arrivedAt() != null ? req.arrivedAt() : LocalDateTime.now();

        if (fireParty) {
            e.setFireArrivedAt(arrivedAt);
            log(e, operator.getRealName(), "消防到场", "消防救援到达现场");
        } else {
            e.setMaintenanceArrivedAt(arrivedAt);
            if (e.getMaintenanceNotifiedAt() != null
                    && Duration.between(e.getMaintenanceNotifiedAt(), arrivedAt).toMinutes() > arriveLimitMinutes) {
                e.setMaintenanceLate(true);
                log(e, operator.getRealName(), "维保到场",
                        "维保人员到场，超过 " + arriveLimitMinutes + " 分钟时限，已标记维保迟到");
            } else {
                log(e, operator.getRealName(), "维保到场", "维保人员到达现场");
            }
            if (e.getStatus() == EventStatus.DISPATCHED) {
                e.setStatus(EventStatus.ARRIVED);
            }
        }
        // 消防先于维保到场 → 自动标记
        if (e.getFireArrivedAt() != null
                && (e.getMaintenanceArrivedAt() == null || e.getFireArrivedAt().isBefore(e.getMaintenanceArrivedAt()))) {
            e.setFireArrivedFirst(true);
        }
        return e;
    }

    // ---------------- 困人释放 ----------------

    @Transactional
    public RescueEvent release(Long id, ReleaseRequest req, User operator) {
        requireRole(operator, "登记困人释放", Role.ADMIN, Role.DUTY, Role.MAINTENANCE);
        RescueEvent e = get(id);
        requireMaintenanceScope(operator, e);
        // 释放仅在场后允许：维保到场（ARRIVED），或消防先到场（DISPATCHED 且消防已到场）
        boolean arrived = e.getStatus() == EventStatus.ARRIVED
                || (e.getStatus() == EventStatus.DISPATCHED && e.getFireArrivedAt() != null);
        if (!arrived) {
            throw new IllegalStateException("维保或消防到场后才能登记困人释放（当前: " + e.getStatus() + "）");
        }

        e.setReleasedAt(req.releasedAt() != null ? req.releasedAt() : LocalDateTime.now());
        e.setDoorOpenMethod(req.doorOpenMethod());
        e.setFaultCode(req.faultCode());
        e.setPassengerHealth(req.passengerHealth());
        e.setMedicalAssistance(req.medicalAssistance());
        e.setRescueNote(req.rescueNote());
        e.setStatus(EventStatus.RELEASED);

        long minutes = Duration.between(e.getAlarmTime(), e.getReleasedAt()).toMinutes();
        log(e, operator.getRealName(), "困人释放",
                "乘客全部救出，救援历时 " + minutes + " 分钟；开门方式: " + req.doorOpenMethod()
                        + "；故障代码: " + req.faultCode()
                        + (Boolean.TRUE.equals(req.medicalAssistance()) ? "；需要医疗协助" : ""));
        return e;
    }

    // ---------------- 复位 / 停梯 / 复检 ----------------

    @Transactional
    public RescueEvent reset(Long id, ResetRequest req, User operator) {
        requireRole(operator, "登记复位复检", Role.ADMIN, Role.DUTY, Role.MAINTENANCE);
        RescueEvent e = get(id);
        requireMaintenanceScope(operator, e);
        requireStatus(e, EventStatus.RELEASED);

        e.setResetAt(req.resetAt() != null ? req.resetAt() : LocalDateTime.now());
        e.setElevatorStopped(Boolean.TRUE.equals(req.elevatorStopped()));
        // 复检资料必须保留：复检结果必填（DTO 校验），复检时间缺省取当前
        e.setRecheckedAt(req.recheckedAt() != null ? req.recheckedAt() : LocalDateTime.now());
        e.setRecheckResult(req.recheckResult());
        e.setStatus(EventStatus.RESET);

        Elevator elevator = e.getElevator();
        elevator.setStatus(Boolean.TRUE.equals(req.elevatorStopped())
                ? Elevator.ElevatorStatus.STOPPED : Elevator.ElevatorStatus.RUNNING);
        elevatorRepo.save(elevator);

        log(e, operator.getRealName(), "复位复检",
                (Boolean.TRUE.equals(req.elevatorStopped())
                        ? "电梯复位但保持停梯，待复检合格后恢复" : "电梯复位并恢复运行")
                        + "；复检结果: " + req.recheckResult());
        return e;
    }

    // ---------------- 关闭归档 ----------------

    @Transactional
    public RescueEvent close(Long id, CloseRequest req, User operator) {
        requireRole(operator, "关闭归档", Role.ADMIN, Role.DUTY);
        RescueEvent e = get(id);
        requireStatus(e, EventStatus.RESET);

        // 责任资料必须保留：责任判定不能为“待定”
        if (req.responsibility() == RescueEvent.Responsibility.PENDING) {
            throw new IllegalArgumentException("关闭前必须完成责任判定，不能为“待定”");
        }
        // 维保相关责任必须留下整改要求
        if ((req.responsibility() == RescueEvent.Responsibility.MAINTENANCE
                || req.responsibility() == RescueEvent.Responsibility.JOINT)
                && (req.rectification() == null || req.rectification().isBlank())) {
            throw new IllegalArgumentException("判定为维保责任或共同责任时，必须填写维保整改要求");
        }

        e.setResponsibility(req.responsibility());
        e.setResponsibilityDetail(req.responsibilityDetail());
        e.setCostBearer(req.costBearer());
        e.setCostAmount(req.costAmount());
        e.setRectification(req.rectification());
        e.setRectificationDeadline(req.rectificationDeadline());
        e.setRectificationDone(false);
        e.setOwnerNotified(Boolean.TRUE.equals(req.ownerNotified()));
        e.setCloseRemark(req.closeRemark());
        e.setClosedAt(LocalDateTime.now());
        e.setStatus(EventStatus.CLOSED);

        // 判定为维保责任 → 维保单位处罚计数 +1，信用分 -5
        if (req.responsibility() == RescueEvent.Responsibility.MAINTENANCE) {
            MaintenanceCompany company = e.getElevator().getCompany();
            if (company != null) {
                company.setPenaltyCount((company.getPenaltyCount() != null ? company.getPenaltyCount() : 0) + 1);
                company.setCreditScore(Math.max(0, (company.getCreditScore() != null ? company.getCreditScore() : 100) - 5));
                companyRepo.save(company);
            }
        }
        log(e, operator.getRealName(), "事件关闭",
                "责任判定: " + responsibilityLabel(req.responsibility())
                + "；费用承担: " + costBearerLabel(req.costBearer()));
        return e;
    }

    // ---------------- 异常标记 ----------------

    @Transactional
    public RescueEvent updateFlags(Long id, FlagsRequest req, User operator) {
        requireRole(operator, "更新异常标记", Role.ADMIN, Role.DUTY, Role.MAINTENANCE);
        RescueEvent e = get(id);
        requireMaintenanceScope(operator, e);
        e.setMaintenanceLate(Boolean.TRUE.equals(req.maintenanceLate()));
        e.setPropertyUnreachable(Boolean.TRUE.equals(req.propertyUnreachable()));
        e.setFireArrivedFirst(Boolean.TRUE.equals(req.fireArrivedFirst()));
        e.setCompensationRequested(Boolean.TRUE.equals(req.compensationRequested()));
        e.setRepeatFault(Boolean.TRUE.equals(req.repeatFault()));
        e.setMisuseClaimed(Boolean.TRUE.equals(req.misuseClaimed()));
        e.setCompensationDetail(req.compensationDetail());
        log(e, operator.getRealName(), "更新异常标记", null);
        return e;
    }

    // ---------------- 通知状态更新 ----------------

    @Transactional
    public void updateNotification(Long eventId, Long notificationId, String status, User operator) {
        EventNotification notification = notificationRepo.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("通知记录不存在"));
        if (!notification.getEvent().getId().equals(eventId)) {
            throw new IllegalArgumentException("通知记录不属于该事件");
        }
        // 值班员/管理员可更新全部通知；维保/管家/保安/消防仅可确认本岗位收到的通知
        Role role = operator.getRole();
        boolean roleAllowed = switch (role) {
            case ADMIN, DUTY -> true;
            case MAINTENANCE, BUTLER, SECURITY, FIRE -> notification.getTargetRole() == role;
            default -> false;
        };
        if (!roleAllowed) {
            throw new AccessDeniedException("当前角色（" + role + "）无权更新该通知");
        }
        requireMaintenanceScope(operator, notification.getEvent());
        requireButlerScope(operator, notification.getEvent());

        EventNotification.NotifyStatus notifyStatus = EventNotification.NotifyStatus.valueOf(status);
        notification.setStatus(notifyStatus);
        if (notifyStatus == EventNotification.NotifyStatus.ACKED) {
            notification.setAcknowledgedAt(LocalDateTime.now());
        }
        notificationRepo.save(notification);
        log(notification.getEvent(), operator.getRealName(), "通知状态更新",
                notification.getTargetName() + " → " + (notifyStatus == EventNotification.NotifyStatus.ACKED ? "已确认"
                        : notifyStatus == EventNotification.NotifyStatus.UNREACHABLE ? "联系不上" : "已通知"));
    }

    // ---------------- 业主回访 ----------------

    @Transactional
    public RescueEvent addFollowup(Long id, FollowupRequest req, User operator) {
        requireRole(operator, "登记业主回访", Role.ADMIN, Role.DUTY, Role.BUTLER);
        RescueEvent e = get(id);
        requireButlerScope(operator, e);
        requireStatus(e, EventStatus.CLOSED);
        OwnerFollowup followup = new OwnerFollowup();
        followup.setEvent(e);
        followup.setOwnerName(req.ownerName());
        followup.setOwnerPhone(req.ownerPhone());
        followup.setFollowupTime(req.followupTime() != null ? req.followupTime() : LocalDateTime.now());
        followup.setSatisfaction(req.satisfaction());
        followup.setFeedback(req.feedback());
        followup.setFollowerName(operator.getRealName());
        followupRepo.save(followup);
        log(e, operator.getRealName(), "业主回访", "回访 " + req.ownerName()
                + (req.satisfaction() != null ? "，满意度 " + req.satisfaction() + " 分" : ""));
        return e;
    }

    // ---------------- 整改完成 ----------------

    @Transactional
    public RescueEvent updateRectification(Long id, RectificationRequest req, User operator) {
        requireRole(operator, "核验整改", Role.ADMIN, Role.DUTY, Role.MAINTENANCE);
        RescueEvent e = get(id);
        requireMaintenanceScope(operator, e);
        e.setRectificationDone(Boolean.TRUE.equals(req.done()));
        log(e, operator.getRealName(), "整改状态更新", Boolean.TRUE.equals(req.done()) ? "维保整改已完成并核验" : "整改状态重置为未完成");
        return e;
    }

    // ---------------- 详情聚合 ----------------

    public Map<String, Object> getDetail(Long id, User user) {
        RescueEvent e = get(id);
        checkReadScope(user, e);
        List<EventCall> calls = callRepo.findByEventIdOrderByCallTimeAsc(id);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("event", e);
        detail.put("calls", calls);
        detail.put("notifications", notificationRepo.findByEventIdOrderByNotifiedAtAsc(id));
        detail.put("logs", logRepo.findByEventIdOrderByCreatedAtAsc(id));
        detail.put("followups", followupRepo.findByEventIdOrderByFollowupTimeAsc(id));
        detail.put("notices", noticeRepo.findByEventIdOrderByPublishedAtDesc(id));
        detail.put("complaints", complaintRepo.findByEventId(id));
        detail.put("parts", partRepo.findByEventId(id));
        // 健康安抚：实时提醒 + 安抚过程摘要（回访/归档可见）
        detail.put("healthAlerts", buildHealthAlerts(e, calls));
        detail.put("comfortSummary", buildComfortSummary(calls));
        return detail;
    }

    /**
     * 健康安抚提醒（按事件当前状态与通话记录实时计算，不落库）：
     * 救援超时 → 提醒联系 120 / 消防优先介入；通话中断 → 提醒重拨并让保安确认回应；
     * 乘客描述不清 → 提醒保持通话并让保安现场确认；恐慌/心脏病/孕妇 → 医疗风险提示；
     * 通话间隔过长 → 提醒保持通话频率。
     */
    private List<Map<String, String>> buildHealthAlerts(RescueEvent e, List<EventCall> calls) {
        List<Map<String, String>> alerts = new ArrayList<>();
        boolean active = e.getStatus() != EventStatus.RELEASED && e.getStatus() != EventStatus.RESET
                && e.getStatus() != EventStatus.CLOSED;
        if (!active) {
            return alerts;
        }
        LocalDateTime now = LocalDateTime.now();

        // 1. 救援超时未释放
        if (e.getAlarmTime() != null && Duration.between(e.getAlarmTime(), now).toMinutes() > rescueAlertMinutes) {
            alerts.add(alert("error", "RESCUE_OVERTIME",
                    "被困已超过 " + rescueAlertMinutes + " 分钟未释放（当前 "
                            + Duration.between(e.getAlarmTime(), now).toMinutes()
                            + " 分钟）：请立即联系 120 医疗待命，并协调消防优先介入"));
        }
        // 2. 通话中断
        if (e.getCallStatus() == RescueEvent.CallStatus.LOST) {
            alerts.add(alert("error", "CALL_LOST",
                    "通话中断：请立即再次拨打轿厢通话，并让现场保安确认轿厢内回应"));
        }
        // 3. 乘客无法清楚描述状态（以最近一次通话为准）
        if (!calls.isEmpty() && Boolean.FALSE.equals(calls.get(calls.size() - 1).getStateClear())) {
            alerts.add(alert("warning", "UNCLEAR_STATE",
                    "乘客无法清楚描述状态：请值班员保持通话，并让保安到现场确认轿厢声音和楼层"));
        }
        // 4. 健康风险（恐慌 / 心脏病 / 孕妇）
        boolean panic = calls.stream().anyMatch(c -> Boolean.TRUE.equals(c.getPanic()));
        boolean heart = calls.stream().anyMatch(c -> Boolean.TRUE.equals(c.getHeartDisease()));
        boolean pregnant = calls.stream().anyMatch(c -> Boolean.TRUE.equals(c.getPregnant()));
        if (panic) {
            alerts.add(alert("warning", "PANIC",
                    "乘客出现恐慌情绪：请保持安抚、缩短通话间隔，并加快救援进度"));
        }
        if (heart || pregnant) {
            List<String> risks = new ArrayList<>();
            if (heart) {
                risks.add("心脏病");
            }
            if (pregnant) {
                risks.add("孕妇");
            }
            alerts.add(alert("error", "MEDICAL_RISK",
                    "轿厢内有" + String.join("、", risks) + "乘客：建议提前联系 120 医疗待命，救援动作从缓从稳"));
        }
        // 5. 通话频率提醒
        if (calls.isEmpty()) {
            if (e.getAlarmTime() != null && Duration.between(e.getAlarmTime(), now).toMinutes() > callRemindMinutes) {
                alerts.add(alert("warning", "NO_CALL",
                        "接警后尚未与乘客通话安抚：请立即拨打轿厢通话，确认乘客状态"));
            }
        } else {
            LocalDateTime lastCall = calls.get(calls.size() - 1).getCallTime();
            if (lastCall != null && Duration.between(lastCall, now).toMinutes() > callRemindMinutes) {
                alerts.add(alert("warning", "CALL_INTERVAL",
                        "已超过 " + callRemindMinutes + " 分钟未与乘客通话：请保持通话频率，持续安抚"));
            }
        }
        return alerts;
    }

    private Map<String, String> alert(String level, String type, String message) {
        Map<String, String> a = new LinkedHashMap<>();
        a.put("level", level);
        a.put("type", type);
        a.put("message", message);
        return a;
    }

    /** 安抚过程摘要：通话次数、通话频率、健康风险标记，供回访与归档查看 */
    private Map<String, Object> buildComfortSummary(List<EventCall> calls) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("callCount", calls.size());
        Double callsPerHour = null;
        Long avgInterval = null;
        if (calls.size() >= 2) {
            LocalDateTime first = calls.get(0).getCallTime();
            LocalDateTime last = calls.get(calls.size() - 1).getCallTime();
            long spanMinutes = Math.max(1, Duration.between(first, last).toMinutes());
            callsPerHour = Math.round(calls.size() * 60.0 / spanMinutes * 10.0) / 10.0;
            avgInterval = Math.round(spanMinutes * 1.0 / (calls.size() - 1));
        }
        summary.put("callsPerHour", callsPerHour);
        summary.put("avgIntervalMinutes", avgInterval);
        summary.put("lastCallTime", calls.isEmpty() ? null : calls.get(calls.size() - 1).getCallTime());
        summary.put("panic", calls.stream().anyMatch(c -> Boolean.TRUE.equals(c.getPanic())));
        summary.put("heartDisease", calls.stream().anyMatch(c -> Boolean.TRUE.equals(c.getHeartDisease())));
        summary.put("pregnant", calls.stream().anyMatch(c -> Boolean.TRUE.equals(c.getPregnant())));
        summary.put("unclearState", calls.stream().anyMatch(c -> Boolean.FALSE.equals(c.getStateClear())));
        return summary;
    }

    // ---------------- 内部方法 ----------------

    private void createNotification(RescueEvent e, Role role, String name, String phone, String note, LocalDateTime now) {
        EventNotification n = new EventNotification();
        n.setEvent(e);
        n.setTargetRole(role);
        n.setTargetName(name);
        n.setTargetPhone(phone);
        n.setNotifiedAt(now);
        n.setNote(note);
        notificationRepo.save(n);
    }

    private void log(RescueEvent e, String actorName, String action, String detail) {
        EventLog l = new EventLog();
        l.setEvent(e);
        l.setActorName(actorName);
        l.setAction(action);
        l.setDetail(detail);
        logRepo.save(l);
    }

    private void requireStatus(RescueEvent e, EventStatus expected) {
        if (e.getStatus() != expected) {
            throw new IllegalStateException("事件当前状态不允许该操作（当前: " + e.getStatus() + "，需要: " + expected + "）");
        }
    }

    private String nextEventNo() {
        String prefix = "EV" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = eventRepo.countByEventNoStartingWith(prefix);
        return prefix + "-" + String.format("%03d", count + 1);
    }

    private String alarmSourceLabel(RescueEvent.AlarmSource source) {
        return switch (source) {
            case IOT -> "物联网报警";
            case PHONE -> "电话求助";
            case PATROL -> "巡查发现";
        };
    }

    private String responsibilityLabel(RescueEvent.Responsibility r) {
        return switch (r) {
            case MAINTENANCE -> "维保单位责任";
            case PROPERTY -> "物业管理责任";
            case OWNER_MISUSE -> "业主使用不当";
            case EQUIPMENT_AGING -> "设备老化";
            case JOINT -> "共同责任";
            case PENDING -> "待定";
        };
    }

    private String costBearerLabel(RescueEvent.CostBearer c) {
        return switch (c) {
            case MAINTENANCE -> "维保单位承担";
            case PROPERTY -> "物业承担";
            case OWNER -> "业主承担";
            case SHARED -> "共同分担";
            case NONE -> "无费用";
        };
    }
}
