package com.elevator.rescue.controller;

import com.elevator.rescue.entity.BuildingNotice;
import com.elevator.rescue.entity.RectificationPlan;
import com.elevator.rescue.entity.User;
import com.elevator.rescue.repository.BuildingNoticeRepository;
import com.elevator.rescue.repository.RectificationPlanRepository;
import com.elevator.rescue.security.AuthHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final BuildingNoticeRepository noticeRepo;
    private final RectificationPlanRepository planRepo;
    private final AuthHelper authHelper;

    /**
     * 公告列表。居民端（业主）仅能看到当前有效（PUBLISHED）公告；
     * 管理角色可查看全部（含已撤回，用于审计），也可按状态过滤。
     */
    @GetMapping
    public List<BuildingNotice> list(@RequestParam(required = false) Long buildingId,
                                     @RequestParam(required = false) Long eventId,
                                     @RequestParam(required = false) BuildingNotice.NoticeStatus status,
                                     Authentication auth) {
        User user = authHelper.requireUser(auth);
        // 居民端只展示有效公告，避免出现“停梯/恢复”相反指引并存
        BuildingNotice.NoticeStatus effectiveStatus = user.getRole() == User.Role.OWNER
                ? BuildingNotice.NoticeStatus.PUBLISHED
                : status;
        if (eventId != null) {
            return noticeRepo.findByEventIdOrderByPublishedAtDesc(eventId).stream()
                    .filter(n -> effectiveStatus == null || n.getStatus() == effectiveStatus)
                    .toList();
        }
        if (buildingId != null) {
            if (effectiveStatus != null) {
                return noticeRepo.findByBuildingIdAndStatusOrderByPublishedAtDesc(buildingId, effectiveStatus);
            }
            return noticeRepo.findByBuildingIdOrderByPublishedAtDesc(buildingId);
        }
        if (effectiveStatus != null) {
            return noticeRepo.findByStatusOrderByPublishedAtDesc(effectiveStatus);
        }
        return noticeRepo.findAllByOrderByPublishedAtDesc();
    }

    @PostMapping
    public BuildingNotice create(@RequestBody BuildingNotice notice, Authentication auth) {
        User publisher = authHelper.requireUser(auth);
        requireManageRole(publisher, "发布公告");
        notice.setId(null);
        notice.setPublisherName(publisher.getRealName());
        notice.setPublishedAt(LocalDateTime.now());
        notice.setStatus(BuildingNotice.NoticeStatus.PUBLISHED);
        // 整改关联与撤回信息只能由整改流程写入，手工公告不得伪造
        notice.setRectificationPlanId(null);
        notice.setRevokedAt(null);
        notice.setRevokeReason(null);
        return noticeRepo.save(notice);
    }

    /**
     * 手动撤回公告。关联整改单且整改未闭环的停梯公告不允许手动撤回——
     * 它只能由「最新方案版本复检通过」的处置自动结束，保证停梯公告持续有效。
     */
    @PutMapping("/{id}/revoke")
    public BuildingNotice revoke(@PathVariable Long id,
                                 @RequestParam(required = false) String reason,
                                 Authentication auth) {
        User operator = authHelper.requireUser(auth);
        requireManageRole(operator, "撤回公告");
        BuildingNotice notice = noticeRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("公告不存在"));
        if (notice.getStatus() == BuildingNotice.NoticeStatus.REVOKED) {
            throw new IllegalStateException("公告已撤回，请勿重复操作");
        }
        if (notice.getType() == BuildingNotice.NoticeType.STOP_NOTICE && notice.getRectificationPlanId() != null) {
            RectificationPlan plan = planRepo.findById(notice.getRectificationPlanId()).orElse(null);
            if (plan != null && plan.getStatus() != RectificationPlan.PlanStatus.RECHECK_PASSED) {
                throw new IllegalStateException("该停梯公告关联进行中的整改单 #" + plan.getId()
                        + "，须最新方案版本复检通过后由系统统一结束，不能手动撤回");
            }
        }
        notice.setStatus(BuildingNotice.NoticeStatus.REVOKED);
        notice.setRevokedAt(LocalDateTime.now());
        notice.setRevokeReason(reason != null && !reason.isBlank()
                ? reason : "由 " + operator.getRealName() + " 手动撤回");
        return noticeRepo.save(notice);
    }

    private void requireManageRole(User user, String action) {
        if (user.getRole() != User.Role.ADMIN && user.getRole() != User.Role.DUTY
                && user.getRole() != User.Role.BUTLER) {
            throw new AccessDeniedException("当前角色（" + user.getRole() + "）无权" + action);
        }
    }
}
