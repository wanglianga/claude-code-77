package com.elevator.rescue.service;

import com.elevator.rescue.entity.*;
import com.elevator.rescue.entity.User.Role;
import com.elevator.rescue.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 反复故障停梯整改：故障自动汇总、整改方案流转、停梯期间老人帮扶。
 */
@Service
@RequiredArgsConstructor
public class RectificationService {

    private final ElevatorRepository elevatorRepo;
    private final RescueEventRepository eventRepo;
    private final PartReplacementRepository partRepo;
    private final OwnerComplaintRepository complaintRepo;
    private final RectificationPlanRepository planRepo;
    private final ElderlyAssistanceRepository assistanceRepo;
    private final BuildingNoticeRepository noticeRepo;
    private final BuildingRepository buildingRepo;

    // ==================== 故障自动汇总 ====================

    /** 一周内多次困人（>=2 次）的电梯预警列表 */
    public List<Map<String, Object>> repeatFaultElevators(User user) {
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Elevator elevator : elevatorRepo.findAll()) {
            if (!inReadScope(user, elevator)) {
                continue;
            }
            List<RescueEvent> weekEvents = eventRepo.findByElevatorIdOrderByAlarmTimeDesc(elevator.getId())
                    .stream().filter(e -> e.getAlarmTime() != null && e.getAlarmTime().isAfter(weekAgo)).toList();
            if (weekEvents.size() >= 2) {
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
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        List<RescueEvent> all = eventRepo.findByElevatorIdOrderByAlarmTimeDesc(elevatorId);
        List<RescueEvent> weekEvents = all.stream()
                .filter(e -> e.getAlarmTime() != null && e.getAlarmTime().isAfter(weekAgo)).toList();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("elevator", elevator);
        summary.put("repeatFault", weekEvents.size() >= 2);
        summary.put("weekEventCount", weekEvents.size());
        summary.put("weekEvents", weekEvents);
        summary.put("faultCodes", faultCodes(weekEvents));
        summary.put("parts", partRepo.findByElevatorIdOrderByReplaceDateDesc(elevatorId));
        summary.put("complaints", complaintRepo.findByElevatorId(elevatorId));
        summary.put("stoppedMinutes", stoppedMinutes(elevator));
        summary.put("plans", planRepo.findByElevatorIdOrderByRequestedAtDesc(elevatorId));
        summary.put("activePlan", activePlan(elevatorId));
        return summary;
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

    // ==================== 整改方案流转 ====================

    /** 物业要求维保单位提交整改方案：电梯保持停用并自动发布楼栋停梯公告 */
    @Transactional
    public RectificationPlan requestPlan(Long elevatorId, Long eventId, String requestNote, User operator) {
        requireRole(operator, "要求提交整改方案", Role.ADMIN, Role.DUTY);
        Elevator elevator = elevatorRepo.findById(elevatorId)
                .orElseThrow(() -> new IllegalArgumentException("电梯不存在"));
        if (activePlan(elevatorId) != null) {
            throw new IllegalStateException("该电梯已存在进行中的整改方案，请先完成复检");
        }

        RectificationPlan plan = new RectificationPlan();
        plan.setElevator(elevator);
        plan.setEventId(eventId);
        plan.setCompanyName(elevator.getCompany() != null ? elevator.getCompany().getName() : "未登记维保单位");
        plan.setRequestNote(requestNote);
        plan.setRequestedBy(operator.getRealName());
        plan.setRequestedAt(LocalDateTime.now());
        plan.setStatus(RectificationPlan.PlanStatus.REQUESTED);
        planRepo.save(plan);

        // 未复检通过前电梯保持停用
        stopElevator(elevator);

        // 自动发布楼栋停梯公告
        publishNotice(elevator, BuildingNotice.NoticeType.STOP_NOTICE,
                elevator.getBuilding().getName() + " " + elevator.getCode() + " 电梯停梯整改公告",
                "因 " + elevator.getCode() + " 电梯短期内多次发生困人故障，物业已要求维保单位（"
                        + plan.getCompanyName() + "）提交整改方案。复检通过前电梯保持停用，"
                        + "请业主使用其他电梯或步行梯；高龄及行动不便业主可联系楼栋管家登记临时帮扶。"
                        + (requestNote != null && !requestNote.isBlank() ? " 整改要求：" + requestNote : ""),
                operator.getRealName(), eventId);
        return plan;
    }

    /** 维保单位提交整改方案：配件、预计到货、复检人、业主公告发布时间必填 */
    @Transactional
    public RectificationPlan submitPlan(Long planId, String parts, java.time.LocalDate expectedArrival,
                                        String recheckInspector, LocalDateTime noticePublishTime,
                                        String planDetail, User operator) {
        requireRole(operator, "提交整改方案", Role.ADMIN, Role.DUTY, Role.MAINTENANCE);
        RectificationPlan plan = getPlan(planId);
        requireCompanyScope(operator, plan.getElevator());
        if (plan.getStatus() != RectificationPlan.PlanStatus.REQUESTED
                && plan.getStatus() != RectificationPlan.PlanStatus.RECHECK_FAILED) {
            throw new IllegalStateException("当前状态不允许提交方案（当前: " + plan.getStatus() + "）");
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

        plan.setParts(parts);
        plan.setExpectedArrival(expectedArrival);
        plan.setRecheckInspector(recheckInspector);
        plan.setNoticePublishTime(noticePublishTime);
        plan.setPlanDetail(planDetail);
        plan.setSubmittedBy(operator.getRealName());
        plan.setSubmittedAt(LocalDateTime.now());
        plan.setStatus(RectificationPlan.PlanStatus.SUBMITTED);
        return planRepo.save(plan);
    }

    /** 复检登记：通过则电梯恢复运行并发布复检公告；未通过则保持停梯 */
    @Transactional
    public RectificationPlan recheck(Long planId, boolean pass, String result, User operator) {
        requireRole(operator, "登记复检结果", Role.ADMIN, Role.DUTY, Role.MAINTENANCE);
        RectificationPlan plan = getPlan(planId);
        requireCompanyScope(operator, plan.getElevator());
        if (plan.getStatus() != RectificationPlan.PlanStatus.SUBMITTED) {
            throw new IllegalStateException("方案未提交，不能登记复检（当前: " + plan.getStatus() + "）");
        }
        if (result == null || result.isBlank()) {
            throw new IllegalArgumentException("请填写复检结果");
        }

        plan.setRecheckedAt(LocalDateTime.now());
        plan.setRecheckResult(result);
        plan.setRecheckedBy(operator.getRealName());
        Elevator elevator = plan.getElevator();

        if (pass) {
            plan.setStatus(RectificationPlan.PlanStatus.RECHECK_PASSED);
            elevator.setStatus(Elevator.ElevatorStatus.RUNNING);
            elevator.setStoppedSince(null);
            elevatorRepo.save(elevator);
            publishNotice(elevator, BuildingNotice.NoticeType.RECHECK,
                    elevator.getBuilding().getName() + " " + elevator.getCode() + " 电梯复检通过恢复运行公告",
                    elevator.getCode() + " 电梯整改已完成并经复检合格（复检人：" + plan.getRecheckInspector()
                            + "），即日起恢复正常运行。感谢业主理解与配合。复检结果：" + result,
                    operator.getRealName(), plan.getEventId());
        } else {
            plan.setStatus(RectificationPlan.PlanStatus.RECHECK_FAILED);
            // 保持停梯，等待维保重新提交方案
            stopElevator(elevator);
        }
        return planRepo.save(plan);
    }

    public List<RectificationPlan> listPlans(User user) {
        return planRepo.findAllByOrderByRequestedAtDesc().stream()
                .filter(p -> inReadScope(user, p.getElevator()))
                .toList();
    }

    public List<RectificationPlan> plansOf(Long elevatorId, User user) {
        Elevator elevator = elevatorRepo.findById(elevatorId)
                .orElseThrow(() -> new IllegalArgumentException("电梯不存在"));
        if (!inReadScope(user, elevator)) {
            throw new AccessDeniedException("无权查看该电梯的整改方案");
        }
        return planRepo.findByElevatorIdOrderByRequestedAtDesc(elevatorId);
    }

    private RectificationPlan getPlan(Long planId) {
        return planRepo.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("整改方案不存在"));
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
                               String title, String content, String publisher, Long eventId) {
        BuildingNotice notice = new BuildingNotice();
        notice.setBuilding(elevator.getBuilding());
        notice.setEventId(eventId);
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
