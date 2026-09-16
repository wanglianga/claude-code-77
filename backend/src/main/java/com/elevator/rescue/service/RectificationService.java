package com.elevator.rescue.service;

import com.elevator.rescue.dto.AssistanceDetail;
import com.elevator.rescue.dto.CurrentBatchView;
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
    private final AssistanceEventRepository assistanceEventRepo;
    private final AssistancePlanLinkRepository assistanceLinkRepo;
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

        // 续办关怀自动纳入新批次：上一次停梯恢复后管家确认「继续关怀」的记录，
        // 关联进本整改单，使本次停梯只汇总「新批次需求 + 明确续办的需求」
        for (ElderlyAssistance continued : assistanceRepo.findByElevatorIdAndStatus(
                elevatorId, ElderlyAssistance.AssistanceStatus.CONTINUED)) {
            AssistancePlanLink link = new AssistancePlanLink();
            link.setAssistance(continued);
            link.setRectificationPlanId(plan.getId());
            assistanceLinkRepo.save(link);
            logAssistance(continued, operator.getRealName(), AssistanceEvent.Action.CARRY_IN,
                    continued.getStatus(), continued.getStatus(),
                    "续办关怀纳入新停梯批次（整改单 #" + plan.getId() + "）");
        }
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

            // 同一处置中：本批次停梯期间登记且未办结的帮扶统一转入「待复核」，
            // 不再计入「停梯期间帮扶中」，等待管家逐条确认结案 / 续办 / 改约
            for (ElderlyAssistance a : assistanceRepo.findByRectificationPlanIdAndStatus(
                    plan.getId(), ElderlyAssistance.AssistanceStatus.ACTIVE)) {
                a.setStatus(ElderlyAssistance.AssistanceStatus.PENDING_REVIEW);
                assistanceRepo.save(a);
                logAssistance(a, null, AssistanceEvent.Action.AUTO_PENDING_REVIEW,
                        ElderlyAssistance.AssistanceStatus.ACTIVE,
                        ElderlyAssistance.AssistanceStatus.PENDING_REVIEW,
                        "整改单 #" + plan.getId() + " 第 " + version.getVersionNo()
                                + " 版复检通过，电梯恢复运行，帮扶需求待管家复核");
            }
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

    /** 申请详情：快照 + 全部版本（含历史失败版本）+ 不可变复检记录 + 公告时间线 + 本批次帮扶记录 */
    public PlanDetail planDetail(Long planId, User user) {
        RectificationPlan plan = getPlan(planId);
        if (!inReadScope(user, plan.getElevator())) {
            throw new AccessDeniedException("无权查看该整改申请");
        }
        List<ElderlyAssistance> carried = assistanceLinkRepo.findByRectificationPlanId(planId).stream()
                .map(AssistancePlanLink::getAssistance)
                .filter(a -> a.getRectificationPlan() == null || !a.getRectificationPlan().getId().equals(planId))
                .toList();
        return new PlanDetail(plan,
                versionRepo.findByPlanIdOrderByVersionNoAsc(planId),
                recheckRecordRepo.findByPlanIdOrderByRecheckedAtAsc(planId),
                noticeRepo.findByRectificationPlanIdOrderByPublishedAtAsc(planId),
                assistanceRepo.findByRectificationPlanIdOrderByCreatedAtDesc(planId),
                carried);
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

    // ==================== 老人上下楼帮扶（按整改单批次交接） ====================

    /**
     * 登记停梯期间老人帮扶：必须关联本电梯当前进行中的整改单（停梯批次），
     * 使帮扶与当次停梯公告、人力安排、复检记录同属一个批次，可互相追溯。
     */
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
        if (elevatorId == null) {
            throw new IllegalArgumentException("请选择关联的停梯电梯");
        }
        Elevator elevator = elevatorRepo.findById(elevatorId)
                .orElseThrow(() -> new IllegalArgumentException("电梯不存在"));
        if (!elevator.getBuilding().getId().equals(buildingId)) {
            throw new IllegalArgumentException("所选电梯不属于该楼栋");
        }
        // 帮扶必须挂在发起它的停梯批次上：电梯须处于停梯整改中
        RectificationPlan batch = activePlan(elevatorId);
        if (batch == null) {
            throw new IllegalStateException("电梯 " + elevator.getCode()
                    + " 当前无进行中的停梯整改单，不能登记停梯帮扶；请先发起停梯整改申请");
        }

        ElderlyAssistance a = new ElderlyAssistance();
        a.setBuilding(building);
        a.setElevator(elevator);
        a.setRectificationPlan(batch);
        a.setResidentName(residentName);
        a.setRoomNo(roomNo);
        a.setPhone(phone);
        a.setNeedDescription(needDescription);
        a.setHelperName(helperName);
        a.setHelperPhone(helperPhone);
        a.setStatus(ElderlyAssistance.AssistanceStatus.ACTIVE);
        a.setCreatedByName(operator.getRealName());
        assistanceRepo.save(a);
        logAssistance(a, operator.getRealName(), AssistanceEvent.Action.CREATE, null,
                ElderlyAssistance.AssistanceStatus.ACTIVE,
                "停梯期间登记帮扶，归入整改单 #" + batch.getId() + "（" + elevator.getCode() + " 本批次）");
        return a;
    }

    /**
     * 管家复核帮扶记录：确认完成 / 继续关怀 / 改约，保留处理人与时间。
     *
     * <ul>
     *   <li>完成：帮扶中 / 待复核 / 续办中 → 已完成（办结）</li>
     *   <li>继续关怀：待复核 → 续办关怀（下次停梯自动纳入新批次汇总）</li>
     *   <li>改约：待复核 / 续办中 → 已改约（约定下次服务时间，本批次结案）</li>
     * </ul>
     */
    @Transactional
    public ElderlyAssistance reviewAssistance(Long id, ElderlyAssistance.ReviewAction action,
                                              String note, LocalDateTime nextAppointmentAt, User operator) {
        requireRole(operator, "复核帮扶记录", Role.ADMIN, Role.DUTY, Role.BUTLER);
        if (action == null) {
            throw new IllegalArgumentException("请选择复核处理方式");
        }
        ElderlyAssistance a = assistanceRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("帮扶登记不存在"));
        requireAssistanceScope(operator, a);

        ElderlyAssistance.AssistanceStatus from = a.getStatus();
        ElderlyAssistance.AssistanceStatus to = switch (action) {
            case COMPLETE -> {
                requireStatus(a, "确认完成", ElderlyAssistance.AssistanceStatus.ACTIVE,
                        ElderlyAssistance.AssistanceStatus.PENDING_REVIEW,
                        ElderlyAssistance.AssistanceStatus.CONTINUED);
                yield ElderlyAssistance.AssistanceStatus.RESOLVED;
            }
            case CONTINUE -> {
                requireStatus(a, "确认继续关怀", ElderlyAssistance.AssistanceStatus.PENDING_REVIEW);
                yield ElderlyAssistance.AssistanceStatus.CONTINUED;
            }
            case RESCHEDULE -> {
                requireStatus(a, "确认改约", ElderlyAssistance.AssistanceStatus.PENDING_REVIEW,
                        ElderlyAssistance.AssistanceStatus.CONTINUED);
                if (nextAppointmentAt == null) {
                    throw new IllegalArgumentException("改约必须填写下次服务时间");
                }
                yield ElderlyAssistance.AssistanceStatus.RESCHEDULED;
            }
        };

        a.setStatus(to);
        a.setReviewedBy(operator.getRealName());
        a.setReviewedAt(LocalDateTime.now());
        a.setReviewAction(action);
        a.setReviewNote(note);
        if (action == ElderlyAssistance.ReviewAction.RESCHEDULE) {
            a.setNextAppointmentAt(nextAppointmentAt);
        }
        if (to == ElderlyAssistance.AssistanceStatus.RESOLVED) {
            a.setResolvedAt(a.getReviewedAt());
        }
        assistanceRepo.save(a);
        logAssistance(a, operator.getRealName(), toEventAction(action), from, to, note);
        return a;
    }

    /** 旧接口兼容：办结 = 复核「确认完成」 */
    @Transactional
    public ElderlyAssistance resolveAssistance(Long id, User operator) {
        return reviewAssistance(id, ElderlyAssistance.ReviewAction.COMPLETE, null, null, operator);
    }

    /**
     * 当前停梯批次帮扶汇总：每个进行中的整改单 = 本批次新登记需求（帮扶中）
     * + 历史批次明确续办转入的关怀。已恢复电梯的旧批次记录不计入人力需求。
     */
    public List<CurrentBatchView> currentBatches(User user) {
        if (user.getRole() == Role.MAINTENANCE) {
            return List.of(); // 维保人员不查看业主帮扶信息
        }
        List<CurrentBatchView> result = new ArrayList<>();
        for (RectificationPlan plan : planRepo.findAllByOrderByRequestedAtDesc()) {
            if (plan.getStatus() == RectificationPlan.PlanStatus.RECHECK_PASSED
                    || !inReadScope(user, plan.getElevator())) {
                continue;
            }
            List<ElderlyAssistance> newBatch = assistanceRepo.findByRectificationPlanIdAndStatus(
                    plan.getId(), ElderlyAssistance.AssistanceStatus.ACTIVE);
            List<ElderlyAssistance> carried = assistanceLinkRepo.findByRectificationPlanId(plan.getId()).stream()
                    .map(AssistancePlanLink::getAssistance)
                    .filter(a -> a.getStatus() == ElderlyAssistance.AssistanceStatus.CONTINUED)
                    .toList();
            result.add(new CurrentBatchView(plan, newBatch, carried));
        }
        return result;
    }

    /** 帮扶详情：记录 + 处理留痕时间线 + 历次纳入的停梯批次（与整改单、公告、复检互相追溯） */
    public AssistanceDetail assistanceDetail(Long id, User user) {
        ElderlyAssistance a = assistanceRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("帮扶登记不存在"));
        if (user.getRole() == Role.MAINTENANCE) {
            throw new AccessDeniedException("维保人员不查看业主帮扶信息");
        }
        if ((user.getRole() == Role.BUTLER || user.getRole() == Role.OWNER)
                && (user.getBuildingId() == null || !user.getBuildingId().equals(a.getBuilding().getId()))) {
            throw new AccessDeniedException("只能查看本楼栋的帮扶记录");
        }
        return new AssistanceDetail(a,
                assistanceEventRepo.findByAssistanceIdOrderByCreatedAtAsc(id),
                assistanceLinkRepo.findByAssistanceIdOrderByLinkedAtAsc(id));
    }

    public List<ElderlyAssistance> listAssistances(Long buildingId, Boolean activeOnly,
                                                   ElderlyAssistance.AssistanceStatus status,
                                                   Long planId, User user) {
        List<ElderlyAssistance> list = planId != null
                ? assistanceRepo.findByRectificationPlanIdOrderByCreatedAtDesc(planId)
                : buildingId != null
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
                .filter(a -> status == null || a.getStatus() == status)
                .filter(a -> !Boolean.TRUE.equals(activeOnly) || a.getStatus() == ElderlyAssistance.AssistanceStatus.ACTIVE)
                .toList();
    }

    private void requireAssistanceScope(User operator, ElderlyAssistance a) {
        if (operator.getRole() == Role.BUTLER
                && (operator.getBuildingId() == null || !operator.getBuildingId().equals(a.getBuilding().getId()))) {
            throw new AccessDeniedException("楼栋管家只能处理本楼栋的帮扶需求");
        }
        if (operator.getRole() == Role.MAINTENANCE || operator.getRole() == Role.OWNER) {
            throw new AccessDeniedException("当前角色无权处理帮扶记录");
        }
    }

    private void requireStatus(ElderlyAssistance a, String action,
                               ElderlyAssistance.AssistanceStatus... allowed) {
        for (ElderlyAssistance.AssistanceStatus s : allowed) {
            if (a.getStatus() == s) {
                return;
            }
        }
        throw new IllegalStateException("当前状态（" + a.getStatus() + "）不允许" + action);
    }

    private AssistanceEvent.Action toEventAction(ElderlyAssistance.ReviewAction action) {
        return switch (action) {
            case COMPLETE -> AssistanceEvent.Action.COMPLETE;
            case CONTINUE -> AssistanceEvent.Action.CONTINUE;
            case RESCHEDULE -> AssistanceEvent.Action.RESCHEDULE;
        };
    }

    private void logAssistance(ElderlyAssistance a, String actorName, AssistanceEvent.Action action,
                               ElderlyAssistance.AssistanceStatus from,
                               ElderlyAssistance.AssistanceStatus to, String note) {
        AssistanceEvent event = new AssistanceEvent();
        event.setAssistance(a);
        event.setActorName(actorName);
        event.setAction(action);
        event.setFromStatus(from);
        event.setToStatus(to);
        event.setNote(note);
        assistanceEventRepo.save(event);
    }
}
