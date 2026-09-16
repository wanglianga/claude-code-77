package com.elevator.rescue.controller;

import com.elevator.rescue.dto.AssistanceDetail;
import com.elevator.rescue.dto.CurrentBatchView;
import com.elevator.rescue.dto.PlanDetail;
import com.elevator.rescue.dto.PlanView;
import com.elevator.rescue.entity.ElderlyAssistance;
import com.elevator.rescue.entity.RecheckRecord;
import com.elevator.rescue.entity.RectificationPlan;
import com.elevator.rescue.entity.RectificationPlanVersion;
import com.elevator.rescue.security.AuthHelper;
import com.elevator.rescue.service.RectificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RectificationController {

    private final RectificationService rectificationService;
    private final AuthHelper authHelper;

    public record PlanRequestDto(Long eventId, String requestNote) {
    }

    public record PlanSubmitDto(
            @NotBlank(message = "请填写需更换的配件") String parts,
            @NotNull(message = "请填写配件预计到货日期") LocalDate expectedArrival,
            @NotBlank(message = "请填写复检人") String recheckInspector,
            @NotNull(message = "请填写业主公告发布时间") LocalDateTime noticePublishTime,
            String planDetail) {
    }

    public record RecheckDto(
            @NotNull(message = "请明确复检是否通过") Boolean pass,
            @NotBlank(message = "请填写复检结果") String result) {
    }

    public record AssistanceDto(
            @NotNull(message = "请选择楼栋") Long buildingId,
            @NotNull(message = "请选择关联的停梯电梯") Long elevatorId,
            @NotBlank(message = "请填写老人/住户姓名") String residentName,
            String roomNo,
            String phone,
            @NotBlank(message = "请填写上下楼需求") String needDescription,
            @NotBlank(message = "请填写临时帮扶人员") String helperName,
            String helperPhone) {
    }

    public record AssistanceReviewDto(
            @NotNull(message = "请选择复核处理方式") ElderlyAssistance.ReviewAction action,
            String note,
            LocalDateTime nextAppointmentAt) {
    }

    // ---------- 故障汇总 ----------

    @GetMapping("/elevators/repeat-faults")
    public List<Map<String, Object>> repeatFaults(Authentication auth) {
        return rectificationService.repeatFaultElevators(authHelper.requireUser(auth));
    }

    @GetMapping("/elevators/{id}/fault-summary")
    public Map<String, Object> faultSummary(@PathVariable Long id, Authentication auth) {
        return rectificationService.faultSummary(id, authHelper.requireUser(auth));
    }

    // ---------- 整改申请与方案版本 ----------

    @GetMapping("/rectification-plans")
    public List<PlanView> listPlans(Authentication auth) {
        return rectificationService.listPlans(authHelper.requireUser(auth));
    }

    @GetMapping("/rectification-plans/{id}")
    public PlanDetail planDetail(@PathVariable Long id, Authentication auth) {
        return rectificationService.planDetail(id, authHelper.requireUser(auth));
    }

    @GetMapping("/rectification-plans/{id}/recheck-records")
    public List<RecheckRecord> recheckRecords(@PathVariable Long id, Authentication auth) {
        return rectificationService.recheckRecords(id, authHelper.requireUser(auth));
    }

    @GetMapping("/elevators/{id}/rectification-plans")
    public List<PlanView> plansOf(@PathVariable Long id, Authentication auth) {
        return rectificationService.plansOf(id, authHelper.requireUser(auth));
    }

    /** 发起停梯整改申请：事务内重新核验同一电梯一周内困人事件达到阈值，并固化证据快照 */
    @PostMapping("/elevators/{id}/rectification-plans")
    public RectificationPlan requestPlan(@PathVariable Long id, @RequestBody PlanRequestDto req, Authentication auth) {
        return rectificationService.requestPlan(id, req.eventId(), req.requestNote(), authHelper.requireUser(auth));
    }

    /** 提交整改方案：每次提交生成新版本行，历史版本（含复检未通过版本）不可覆盖 */
    @PostMapping("/rectification-plans/{id}/versions")
    public RectificationPlanVersion submitVersion(@PathVariable Long id, @Valid @RequestBody PlanSubmitDto req,
                                                  Authentication auth) {
        return rectificationService.submitVersion(id, req.parts(), req.expectedArrival(), req.recheckInspector(),
                req.noticePublishTime(), req.planDetail(), authHelper.requireUser(auth));
    }

    /** 复检登记：仅允许对最新版本登记一次，生成不可变复检记录；仅最新版通过才恢复运行并发布复检公告 */
    @PostMapping("/rectification-versions/{versionId}/recheck")
    public RectificationPlanVersion recheck(@PathVariable Long versionId, @Valid @RequestBody RecheckDto req,
                                            Authentication auth) {
        return rectificationService.recheck(versionId, Boolean.TRUE.equals(req.pass()), req.result(),
                authHelper.requireUser(auth));
    }

    // ---------- 老人帮扶（按整改单批次交接） ----------

    @GetMapping("/assistances")
    public List<ElderlyAssistance> listAssistances(@RequestParam(required = false) Long buildingId,
                                                   @RequestParam(required = false) Boolean activeOnly,
                                                   @RequestParam(required = false) ElderlyAssistance.AssistanceStatus status,
                                                   @RequestParam(required = false) Long planId,
                                                   Authentication auth) {
        return rectificationService.listAssistances(buildingId, activeOnly, status, planId,
                authHelper.requireUser(auth));
    }

    /** 当前停梯批次帮扶汇总：每个进行中整改单 = 本批次新需求 + 历史批次续办转入 */
    @GetMapping("/assistances/current-batches")
    public List<CurrentBatchView> currentBatches(Authentication auth) {
        return rectificationService.currentBatches(authHelper.requireUser(auth));
    }

    /** 帮扶详情：处理留痕时间线 + 历次纳入批次，与整改单/公告/复检互相追溯 */
    @GetMapping("/assistances/{id}")
    public AssistanceDetail assistanceDetail(@PathVariable Long id, Authentication auth) {
        return rectificationService.assistanceDetail(id, authHelper.requireUser(auth));
    }

    @PostMapping("/assistances")
    public ElderlyAssistance createAssistance(@Valid @RequestBody AssistanceDto req, Authentication auth) {
        return rectificationService.createAssistance(req.buildingId(), req.elevatorId(), req.residentName(),
                req.roomNo(), req.phone(), req.needDescription(), req.helperName(), req.helperPhone(),
                authHelper.requireUser(auth));
    }

    /** 管家复核：确认完成 / 继续关怀 / 改约，保留处理人与时间 */
    @PostMapping("/assistances/{id}/review")
    public ElderlyAssistance reviewAssistance(@PathVariable Long id,
                                              @Valid @RequestBody AssistanceReviewDto req,
                                              Authentication auth) {
        return rectificationService.reviewAssistance(id, req.action(), req.note(), req.nextAppointmentAt(),
                authHelper.requireUser(auth));
    }

    @PutMapping("/assistances/{id}/resolve")
    public ElderlyAssistance resolveAssistance(@PathVariable Long id, Authentication auth) {
        return rectificationService.resolveAssistance(id, authHelper.requireUser(auth));
    }
}
