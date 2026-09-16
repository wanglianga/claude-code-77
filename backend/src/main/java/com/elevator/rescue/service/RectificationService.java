package com.elevator.rescue.service;

import com.elevator.rescue.dto.PlanDetail;
import com.elevator.rescue.dto.PlanView;
import com.elevator.rescue.entity.*;
import com.elevator.rescue.entity.User.Role;
import com.elevator.rescue.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 反复故障停梯整改：故障自动汇总、整改申请（触发证据核验+快照）、
 * 方案版本流转（历史版本不可覆盖）、不可变复检记录、停梯期间老人帮扶。
 */
@Service
@RequiredArgsConstructor
public class RectificationService {

    private final ElevatorRepository elevatorRepo;
    private final RescueEventRepository eventRepo;
    private final PartReplacementRepository partRepo;
    private final OwnerComplaintRepository complaintRepo;
    private final RectificationPlanRepository planRepo;
    private final RectificationPlanVersionRepository versionRepo;
    private final RecheckRecordRepository recheckRecordRepo;
    private final ElderlyAssistanceRepository assistanceRepo;
    private final BuildingNoticeRepository noticeRepo;
    private final BuildingRepository buildingRepo;
    private final ObjectMapper objectMapper;

    /** 整改申请触发阈值：同一电梯窗口期内困人事件达到该起数才允许申请 */
    @Value("${app.rectification.trigger-threshold:2}")
    private int triggerThreshold;

    /** 触发证据窗口（天） */
    @Value("${app.rectification.trigger-window-days:7}")
    private int triggerWindowDays;

    // ==================== 故障自动汇总 ====================

    /** 一周内多次困人（达到阈值）的电梯预警列表 */
    public List<Map<String, Object>> repeatFaultElevators(User user) {
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(triggerWindowDays);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Elevator elevator : elevatorRepo.findAll()) {
            if (!inReadScope(user, elevator)) {
                continue;
            }
            List<RescueEvent> weekEvents = weekEvents(elevator.getId(), weekAgo);
            if (weekEvents.size() >= triggerThreshold) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("elevator", elevator);
                item.put("weekCount", weekEvents.size());
                item.put("stoppedMinutes", stoppedMinutes(elevator));
                item.put("activePlan", activePlan(elevator.getId()));
                item.put("faultCodes", faultCodes(weekEvents));
                result.add(item);
            }
        }
        return result;
    }

    /** 单台电梯故障汇总：困人事件、故障代码、维保记录、停梯时长、业主投诉 */
    public Map<String, Object> faultSummary(Long elevatorId, User user) {
        Elevator elevator = elevatorRepo.findById(elevatorId)
                .orElseThrow(() -> new IllegalArgumentException("电梯不存在"));
        if (!inReadScope(user, elevator)) {
            throw new AccessDeniedException("无权查看该电梯的故障汇总资料");
        }
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(triggerWindowDays);
        List<RescueEvent> weekEvents = weekEvents(elevatorId, weekAgo);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("elevator", elevator);
        summary.put("repeatFault", weekEvents.size() >= triggerThreshold);
        summary.put("triggerThreshold", triggerThreshold);
        summary.put("triggerWindowDays", triggerWindowDays);
        summary.put("weekEventCount", weekEvents.size());
        summary.put("weekEvents", weekEvents);
        summary.put("faultCodes", faultCodes(weekEvents));
        summary.put("parts", partRepo.findByElevatorIdOrderByReplaceDateDesc(elevatorId));
        summary.put("complaints", complaintRepo.findByElevatorId(elevatorId));
        summary.put("stoppedMinutes", stoppedMinutes(elevator));
        summary.put("plans", planViews(planRepo.findByElevatorIdOrderByRequestedAtDesc(elevatorId)));
        summary.put("activePlan", activePlan(elevatorId));
        return summary;
    }

    private List<RescueEvent> weekEvents(Long elevatorId, LocalDateTime since) {
        return eventRepo.findByElevatorIdOrderByAlarmTimeDesc(elevatorId)
                .stream().filter(e -> e.getAlarmTime() != null && !e.getAlarmTime().isBefore(since)).toList();
    }

    private List<String> faultCodes(List<RescueEvent> events) {
        return events.stream()
                .map(RescueEvent::getFaultCode)
                .filter(Objects::nonNull)
                .filter(c -> !c.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    private Long stoppedMinutes(Elevator elevator) {
        if (elevator.getStoppedSince() == null) {
            return null;
        }
        return Duration.between(elevator.getStoppedSince(), LocalDateTime.now()).toMinutes();
    }

    /** 进行中的整改申请（最新版未复检通过即视为进行中） */
    private RectificationPlan activePlan(Long elevatorId) {
        return planRepo.findByElevatorIdOrderByRequestedAtDesc(elevatorId).stream()
                .filter(p -> p.getStatus() != RectificationPlan.PlanStatus.RECHECK_PASSED)
                .findFirst().orElse(null);
    }

    /** 资料门禁：维保仅本单位电梯，管家/业主仅本楼栋电梯 */
    private boolean inReadScope(User user, Elevator elevator) {
        return switch (user.getRole()) {
            case MAINTENANCE -> user.getCompanyId() != null && elevator.getCompany() != null
                    && user.getCompanyId().equals(elevator.getCompany().getId());
            case BUTLER, OWNER -> user.getBuildingId() != null
                    && user.getBuildingId().equals(elevator.getBuilding().getId());
            default -> true;
        };
    }

    private void requireRole(User user, String action, Role... roles) {
        for (Role role : roles) {
            if (user.getRole() == role) {
                return;
            }
        }
        throw new AccessDeniedException("当前角色（" + user.getRole() + "）无权" + action);
    }

    private void requireCompanyScope(User user, Elevator elevator) {
        if (user.getRole() != Role.MAINTENANCE) {
            return;
        }
        Long companyId = elevator.getCompany() != null ? elevator.getCompany().getId() : null;
        if (user.getCompanyId() == null || !user.getCompanyId().equals(companyId)) {
            throw new AccessDeniedException("维保人员只能操作本维保单位负责电梯的整改方案");
        }
    }

    // ==================== 整改申请（触发证据核验 + 快照固化） ====================

    /**
     * 物业发起停梯整改申请。
     *
     * <p>事务内锁定电梯行并重新核验：同一电梯最近 {@code triggerWindowDays} 天内
     * 困人事件达到 {@code triggerThreshold} 起才允许申请，否则整体回滚（4xx），
     * 方案、停梯状态、公告均不变。核验通过后同事务固化触发事件、故障代码、
     * 投诉汇总快照，电梯保持停用并自动发布楼栋停梯公告。</p>
     */
    @Transactional
    public RectificationPlan requestPlan(Long elevatorId, Long eventId, String requestNote, User operator) {
        requireRole(operator, "发起停梯整改申请", Role.ADMIN, Role.DUTY);
        // 事务内锁定电梯行：同一电梯的申请核验与创建串行化，防止并发下重复申请或证据失真
        Elevator elevator = elevatorRepo.findByIdForUpdate(elevatorId)
                .orElseThrow(() -> new IllegalArgumentException("电梯不存在"));
        if (activePlan(elevatorId) != null) {
            throw new IllegalStateException("该电梯已存在进行中的整改申请，请先完成复检");
        }

        // ---- 事务内重新核验触发证据 ----
        LocalDateTime windowEnd = LocalDateTime.now();
        LocalDateTime windowStart = windowEnd.minusDays(triggerWindowDays);
        List<RescueEvent> triggerEvents = eventRepo.findByElevatorIdOrderByAlarmTimeDesc(elevatorId).stream()
                .filter(e -> e.getAlarmTime() != null
                        && !e.getAlarmTime().isBefore(windowStart)
                        && !e.getAlarmTime().isAfter(windowEnd))
                .toList();
        if (triggerEvents.size() < triggerThreshold) {
            throw new IllegalStateException("触发证据不足：电梯 " + elevator.getCode()
                    + " 最近 " + triggerWindowDays + " 天内困人事件 " + triggerEvents.size()
                    + " 起，未达到整改申请阈值 " + triggerThreshold + " 起，不允许发起停梯整改申请");
        }

        // ---- 固化快照：触发事件、故障代码、投诉汇总（窗口内） ----
        List<String> faultCodes = faultCodes(triggerEvents);
        List<Map<String, Object>> eventSnapshot = triggerEvents.stream()
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<String, Object>();
                    item.put("id", e.getId());
                    item.put("eventNo", e.getEventNo());
                    item.put("faultCode", e.getFaultCode());
                    item.put("alarmTime", e.getAlarmTime() != null ? e.getAlarmTime().toString() : null);
                    item.put("status", e.getStatus() != null ? e.getStatus().name() : null);
                    return item;
                })
                .collect(Collectors.toList());
        List<OwnerComplaint> windowComplaints = complaintRepo.findByElevatorId(elevatorId).stream()
                .filter(c -> c.getCreatedAt() != null
                        && !c.getCreatedAt().isBefore(windowStart)
                        && !c.getCreatedAt().isAfter(windowEnd))
                .toList();
        Map<String, Object> complaintSummary = new LinkedHashMap<>();
        complaintSummary.put("windowDays", triggerWindowDays);
        complaintSummary.put("count", windowComplaints.size());
        complaintSummary.put("items", windowComplaints.stream()
                .map(c -> {
                    Map<String, Object> item = new LinkedHashMap<String, Object>();
                    item.put("id", c.getId());
                    item.put("ownerName", c.getOwnerName());
                    item.put("content", c.getContent());
                    item.put("status", c.getStatus() != null ? c.getStatus().name() : null);
                    item.put("createdAt", c.getCreatedAt().toString());
                    return item;
                })
                .collect(Collectors.toList()));

        RectificationPlan plan = new RectificationPlan();
        plan.setElevator(elevator);
        plan.setEventId(eventId);
        plan.setCompanyName(elevator.getCompany() != null ? elevator.getCompany().getName() : "未登记维保单位");
        plan.setRequestNote(requestNote);
        plan.setRequestedBy(operator.getRealName());
        plan.setRequestedAt(windowEnd);
        plan.setStatus(RectificationPlan.PlanStatus.REQUESTED);
        plan.setTriggerThreshold(triggerThreshold);
        plan.setEvidenceWindowStart(windowStart);
        plan.setEvidenceWindowEnd(windowEnd);
        plan.setTriggerEventCount(triggerEvents.size());
        plan.setTriggerEventsJson(toJson(eventSnapshot));
        plan.setFaultCodesJson(toJson(faultCodes));
        plan.setComplaintSummaryJson(toJson(complaintSummary));
        plan.setCurrentVersionNo(0);
        planRepo.save(plan);

        // 未复检通过前电梯保持停用
        stopElevator(elevator);

        // 自动发布楼栋停梯公告（关联本整改单，复检通过前持续有效）
        publishNotice(elevator, BuildingNotice.NoticeType.STOP_NOTICE,
                elevator.getBuilding().getName() + " " + elevator.getCode() + " 电梯停梯整改公告",
                "因 " + elevator.getCode() + " 电梯最近 " + triggerWindowDays + " 天内发生 "
                        + triggerEvents.size() + " 起困人故障（故障代码：" + String.join("、", faultCodes)
                        + "），物业已要求维保单位（" + plan.getCompanyName()
                        + "）提交整改方案。复检通过前电梯保持停用，"
                        + "请业主使用其他电梯或步行梯；高龄及行动不便业主可联系楼栋管家登记临时帮扶。"
                        + (requestNote != null && !requestNote.isBlank() ? " 整改要求：" + requestNote : ""),
                operator.getRealName(), eventId, plan.getId());
        return plan;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("触发证据快照序列化失败", e);
        }
    }

    // ==================== 方案版本流转 ====================

    /**
     * 维保单位提交整改方案版本：每次提交生成新版本行（versionNo 递增），
     * 历史版本（含复检未通过版本）及其字段永不覆盖。
     * 仅当申请处于「待提交 / 最新版复检未通过」时允许提交。
     */
    @Transactional
    public RectificationPlanVersion submitVersion(Long planId, String parts, LocalDate expectedArrival,
                                                  String recheckInspector, LocalDateTime noticePublishTime,
                                                  String planDetail, User operator) {
        requireRole(operator, "提交整改方案", Role.ADMIN, Role.DUTY, Role.MAINTENANCE);
        RectificationPlan plan = getPlanForUpdate(planId);
        requireCompanyScope(operator, plan.getElevator());
        if (plan.getStatus() == RectificationPlan.PlanStatus.RECHECK_PASSED) {
            throw new IllegalStateException("该整改申请已复检通过闭环，不能再提交方案版本");
        }
        if (plan.getStatus() == RectificationPlan.PlanStatus.SUBMITTED) {
            throw new IllegalStateException("第 " + plan.getCurrentVersionNo()
                    + " 版方案待复检，复检结论出具前不能提交新版本");
        }
        if (parts == null || parts.isBlank()) {
            throw new IllegalArgumentException("整改方案必须写明需更换的配件");
        }
        if (expectedArrival == null) {
            throw new IllegalArgumentException("整改方案必须写明配件预计到货日期");
        }
        if (recheckInspector == null || recheckInspector.isBlank()) {
            throw new IllegalArgumentException("整改方案必须写明复检人");
        }
        if (noticePublishTime == null) {
            throw new IllegalArgumentException("整改方案必须写明业主公告发布时间");
        }

        int versionNo = plan.getCurrentVersionNo() + 1;
        RectificationPlanVersion version = new RectificationPlanVersion();
        version.setPlan(plan);
        version.setVersionNo(versionNo);
        version.setParts(parts);
        version.setExpectedArrival(expectedArrival);
        version.setRecheckInspector(recheckInspector);
        version.setNoticePublishTime(noticePublishTime);
        version.setPlanDetail(planDetail);
        version.setSubmittedBy(operator.getRealName());
        version.setSubmittedAt(LocalDateTime.now());
        version.setStatus(RectificationPlanVersion.VersionStatus.SUBMITTED);
        versionRepo.save(version);

        plan.setCurrentVersionNo(versionNo);
        plan.setStatus(RectificationPlan.PlanStatus.SUBMITTED);
        planRepo.save(plan);
        return version;
    }

    /**
     * 复检登记：只允许对申请的最新版本登记，且每版仅登记一次。
     * 登记生成不可变复检记录；通过则电梯恢复运行并发布复检公告，
     * 未通过则版本标记失败（结论不可更改），电梯与停梯公告持续有效。
     */
    @Transactional
    public RectificationPlanVersion recheck(Long versionId, boolean pass, String result, User operator) {
        requireRole(operator, "登记复检结果", Role.ADMIN, Role.DUTY, Role.MAINTENANCE);
        RectificationPlanVersion version = versionRepo.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("方案版本不存在"));
        RectificationPlan plan = getPlanForUpdate(version.getPlan().getId());
        requireCompanyScope(operator, plan.getElevator());
        if (!plan.getCurrentVersionNo().equals(version.getVersionNo())) {
            throw new IllegalStateException("只能对最新版本（第 " + plan.getCurrentVersionNo()
                    + " 版）登记复检；历史版本结论已固化，不可更改");
        }
        if (version.getStatus() != RectificationPlanVersion.VersionStatus.SUBMITTED) {
            throw new IllegalStateException("该版本复检结论已出具（"
                    + (version.getStatus() == RectificationPlanVersion.VersionStatus.RECHECK_PASSED ? "通过" : "未通过")
                    + "），复检记录不可更改");
        }
        if (result == null || result.isBlank()) {
            throw new IllegalArgumentException("请填写复检结果");
        }

        // 不可变复检记录（只增不改）
        RecheckRecord record = new RecheckRecord();
        record.setVersion(version);
        record.setPlanId(plan.getId());
        record.setPass(pass);
        record.setResult(result);
        record.setRecheckedBy(operator.getRealName());
        record.setRecheckedAt(LocalDateTime.now());
        recheckRecordRepo.save(record);

        // 版本结论写入一次，此后不可变
        version.setRecheckedAt(record.getRecheckedAt());
        version.setRecheckResult(result);
        version.setRecheckedBy(operator.getRealName());
        Elevator elevator = plan.getElevator();

        if (pass) {
            version.setStatus(RectificationPlanVersion.VersionStatus.RECHECK_PASSED);
            plan.setStatus(RectificationPlan.PlanStatus.RECHECK_PASSED);
            // 仅最新版本复检通过才允许恢复运行
            elevator.setStatus(Elevator.ElevatorStatus.RUNNING);
            elevator.setStoppedSince(null);
            elevatorRepo.save(elevator);
            // 同一处置中：结束本整改单关联的有效停梯公告（撤回但保留可审计），
            // 再发布唯一有效的恢复公告，避免居民收到相反出行指引
            for (BuildingNotice stopNotice : noticeRepo.findByRectificationPlanIdAndTypeAndStatus(
                    plan.getId(), BuildingNotice.NoticeType.STOP_NOTICE, BuildingNotice.NoticeStatus.PUBLISHED)) {
                stopNotice.setStatus(BuildingNotice.NoticeStatus.REVOKED);
                stopNotice.setRevokedAt(record.getRecheckedAt());
                stopNotice.setRevokeReason("整改方案第 " + version.getVersionNo()
                        + " 版复检通过，电梯恢复运行，停梯公告自动结束");
                noticeRepo.save(stopNotice);
            }
            publishNotice(elevator, BuildingNotice.NoticeType.RECHECK,
                    elevator.getBuilding().getName() + " " + elevator.getCode() + " 电梯复检通过恢复运行公告",
                    elevator.getCode() + " 电梯整改方案第 " + version.getVersionNo()
                            + " 版已完成并经复检合格（复检人：" + version.getRecheckInspector()
                            + "），即日起恢复正常运行。感谢业主理解与配合。复检结果：" + result,
                    operator.getRealName(), plan.getEventId(), plan.getId());
        } else {
            version.setStatus(RectificationPlanVersion.VersionStatus.RECHECK_FAILED);
            plan.setStatus(RectificationPlan.PlanStatus.RECHECK_FAILED);
            // 保持停梯（停梯起始时间不变），关联停梯公告持续有效，不发布任何恢复通知
            stopElevator(elevator);
        }
        versionRepo.save(version);
        planRepo.save(plan);
        return version;
    }

    // ==================== 查询 ====================

    public List<PlanView> listPlans(User user) {
        return planViews(planRepo.findAllByOrderByRequestedAtDesc().stream()
                .filter(p -> inReadScope(user, p.getElevator()))
                .toList());
    }

    public List<PlanView> plansOf(Long elevatorId, User user) {
        Elevator elevator = elevatorRepo.findById(elevatorId)
                .orElseThrow(() -> new IllegalArgumentException("电梯不存在"));
        if (!inReadScope(user, elevator)) {
            throw new AccessDeniedException("无权查看该电梯的整改方案");
        }
        return planViews(planRepo.findByElevatorIdOrderByRequestedAtDesc(elevatorId));
    }

    /** 申请详情：快照 + 全部版本（含历史失败版本）+ 不可变复检记录 + 公告时间线 */
    public PlanDetail planDetail(Long planId, User user) {
        RectificationPlan plan = getPlan(planId);
        if (!inReadScope(user, plan.getElevator())) {
            throw new AccessDeniedException("无权查看该整改申请");
        }
        return new PlanDetail(plan,
                versionRepo.findByPlanIdOrderByVersionNoAsc(planId),
                recheckRecordRepo.findByPlanIdOrderByRecheckedAtAsc(planId),
                noticeRepo.findByRectificationPlanIdOrderByPublishedAtAsc(planId));
    }

    public List<RecheckRecord> recheckRecords(Long planId, User user) {
        RectificationPlan plan = getPlan(planId);
        if (!inReadScope(user, plan.getElevator())) {
            throw new AccessDeniedException("无权查看该整改申请的复检记录");
        }
        return recheckRecordRepo.findByPlanIdOrderByRecheckedAtAsc(planId);
    }

    private List<PlanView> planViews(List<RectificationPlan> plans) {
        return plans.stream()
                .map(p -> {
                    List<RectificationPlanVersion> versions = versionRepo.findByPlanIdOrderByVersionNoAsc(p.getId());
                    RectificationPlanVersion latest = versions.isEmpty() ? null : versions.get(versions.size() - 1);
                    return new PlanView(p, latest, versions.size());
                })
                .collect(Collectors.toList());
    }

    private RectificationPlan getPlan(Long planId) {
        return planRepo.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("整改申请不存在"));
    }

    private RectificationPlan getPlanForUpdate(Long planId) {
        return planRepo.findByIdForUpdate(planId)
                .orElseThrow(() -> new IllegalArgumentException("整改申请不存在"));
    }

    private void stopElevator(Elevator elevator) {
        if (elevator.getStatus() != Elevator.ElevatorStatus.STOPPED) {
            elevator.setStatus(Elevator.ElevatorStatus.STOPPED);
        }
        if (elevator.getStoppedSince() == null) {
            elevator.setStoppedSince(LocalDateTime.now());
        }
        elevatorRepo.save(elevator);
    }

    private void publishNotice(Elevator elevator, BuildingNotice.NoticeType type,
                               String title, String content, String publisher, Long eventId, Long rectificationPlanId) {
        BuildingNotice notice = new BuildingNotice();
        notice.setBuilding(elevator.getBuilding());
        notice.setEventId(eventId);
        notice.setRectificationPlanId(rectificationPlanId);
        notice.setType(type);
        notice.setTitle(title);
        notice.setContent(content);
        notice.setPublisherName(publisher);
        notice.setPublishedAt(LocalDateTime.now());
        noticeRepo.save(notice);
    }

    // ==================== 老人上下楼帮扶 ====================

    @Transactional
    public ElderlyAssistance createAssistance(Long buildingId, Long elevatorId, String residentName,
                                              String roomNo, String phone, String needDescription,
                                              String helperName, String helperPhone, User operator) {
        requireRole(operator, "登记老人帮扶", Role.ADMIN, Role.DUTY, Role.BUTLER);
        Building building = buildingRepo.findById(buildingId)
                .orElseThrow(() -> new IllegalArgumentException("楼栋不存在"));
        if (operator.getRole() == Role.BUTLER
                && (operator.getBuildingId() == null || !operator.getBuildingId().equals(buildingId))) {
            throw new AccessDeniedException("楼栋管家只能登记本楼栋的帮扶需求");
        }
        if (residentName == null || residentName.isBlank()) {
            throw new IllegalArgumentException("请填写老人/住户姓名");
        }
        if (needDescription == null || needDescription.isBlank()) {
            throw new IllegalArgumentException("请填写上下楼需求");
        }
        if (helperName == null || helperName.isBlank()) {
            throw new IllegalArgumentException("请填写临时帮扶人员");
        }

        ElderlyAssistance a = new ElderlyAssistance();
        a.setBuilding(building);
        if (elevatorId != null) {
            Elevator elevator = elevatorRepo.findById(elevatorId)
                    .orElseThrow(() -> new IllegalArgumentException("电梯不存在"));
            a.setElevator(elevator);
        }
        a.setResidentName(residentName);
        a.setRoomNo(roomNo);
        a.setPhone(phone);
        a.setNeedDescription(needDescription);
        a.setHelperName(helperName);
        a.setHelperPhone(helperPhone);
        a.setStatus(ElderlyAssistance.AssistanceStatus.ACTIVE);
        return assistanceRepo.save(a);
    }

    @Transactional
    public ElderlyAssistance resolveAssistance(Long id, User operator) {
        requireRole(operator, "办结帮扶登记", Role.ADMIN, Role.DUTY, Role.BUTLER);
        ElderlyAssistance a = assistanceRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("帮扶登记不存在"));
        if (operator.getRole() == Role.BUTLER
                && (operator.getBuildingId() == null || !operator.getBuildingId().equals(a.getBuilding().getId()))) {
            throw new AccessDeniedException("楼栋管家只能办结本楼栋的帮扶需求");
        }
        a.setStatus(ElderlyAssistance.AssistanceStatus.RESOLVED);
        a.setResolvedAt(LocalDateTime.now());
        return assistanceRepo.save(a);
    }

    public List<ElderlyAssistance> listAssistances(Long buildingId, Boolean activeOnly, User user) {
        List<ElderlyAssistance> list = buildingId != null
                ? assistanceRepo.findByBuildingIdOrderByCreatedAtDesc(buildingId)
                : assistanceRepo.findAllByOrderByCreatedAtDesc();
        return list.stream()
                .filter(a -> {
                    if (user.getRole() == Role.MAINTENANCE) {
                        return false; // 维保人员不查看业主帮扶信息
                    }
                    if ((user.getRole() == Role.BUTLER || user.getRole() == Role.OWNER)
                            && user.getBuildingId() != null) {
                        return user.getBuildingId().equals(a.getBuilding().getId());
                    }
                    return true;
                })
                .filter(a -> !Boolean.TRUE.equals(activeOnly) || a.getStatus() == ElderlyAssistance.AssistanceStatus.ACTIVE)
                .toList();
    }
}
