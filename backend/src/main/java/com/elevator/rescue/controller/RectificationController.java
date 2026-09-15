package com.elevator.rescue.controller;

import com.elevator.rescue.entity.ElderlyAssistance;
import com.elevator.rescue.entity.RectificationPlan;
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
            Long elevatorId,
            @NotBlank(message = "请填写老人/住户姓名") String residentName,
            String roomNo,
            String phone,
            @NotBlank(message = "请填写上下楼需求") String needDescription,
            @NotBlank(message = "请填写临时帮扶人员") String helperName,
            String helperPhone) {
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

    // ---------- 整改方案 ----------

    @GetMapping("/rectification-plans")
    public List<RectificationPlan> listPlans(Authentication auth) {
        return rectificationService.listPlans(authHelper.requireUser(auth));
    }

    @GetMapping("/elevators/{id}/rectification-plans")
    public List<RectificationPlan> plansOf(@PathVariable Long id, Authentication auth) {
        return rectificationService.plansOf(id, authHelper.requireUser(auth));
    }

    @PostMapping("/elevators/{id}/rectification-plans")
    public RectificationPlan requestPlan(@PathVariable Long id, @RequestBody PlanRequestDto req, Authentication auth) {
        return rectificationService.requestPlan(id, req.eventId(), req.requestNote(), authHelper.requireUser(auth));
    }

    @PutMapping("/rectification-plans/{id}/submit")
    public RectificationPlan submitPlan(@PathVariable Long id, @Valid @RequestBody PlanSubmitDto req, Authentication auth) {
        return rectificationService.submitPlan(id, req.parts(), req.expectedArrival(), req.recheckInspector(),
                req.noticePublishTime(), req.planDetail(), authHelper.requireUser(auth));
    }

    @PutMapping("/rectification-plans/{id}/recheck")
    public RectificationPlan recheck(@PathVariable Long id, @Valid @RequestBody RecheckDto req, Authentication auth) {
        return rectificationService.recheck(id, Boolean.TRUE.equals(req.pass()), req.result(), authHelper.requireUser(auth));
    }

    // ---------- 老人帮扶 ----------

    @GetMapping("/assistances")
    public List<ElderlyAssistance> listAssistances(@RequestParam(required = false) Long buildingId,
                                                   @RequestParam(required = false) Boolean activeOnly,
                                                   Authentication auth) {
        return rectificationService.listAssistances(buildingId, activeOnly, authHelper.requireUser(auth));
    }

    @PostMapping("/assistances")
    public ElderlyAssistance createAssistance(@Valid @RequestBody AssistanceDto req, Authentication auth) {
        return rectificationService.createAssistance(req.buildingId(), req.elevatorId(), req.residentName(),
                req.roomNo(), req.phone(), req.needDescription(), req.helperName(), req.helperPhone(),
                authHelper.requireUser(auth));
    }

    @PutMapping("/assistances/{id}/resolve")
    public ElderlyAssistance resolveAssistance(@PathVariable Long id, Authentication auth) {
        return rectificationService.resolveAssistance(id, authHelper.requireUser(auth));
    }
}
