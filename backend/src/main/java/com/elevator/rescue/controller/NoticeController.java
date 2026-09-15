package com.elevator.rescue.controller;

import com.elevator.rescue.entity.BuildingNotice;
import com.elevator.rescue.entity.User;
import com.elevator.rescue.repository.BuildingNoticeRepository;
import com.elevator.rescue.security.AuthHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final BuildingNoticeRepository noticeRepo;
    private final AuthHelper authHelper;

    @GetMapping
    public List<BuildingNotice> list(@RequestParam(required = false) Long buildingId,
                                     @RequestParam(required = false) Long eventId) {
        if (eventId != null) {
            return noticeRepo.findByEventIdOrderByPublishedAtDesc(eventId);
        }
        if (buildingId != null) {
            return noticeRepo.findByBuildingIdOrderByPublishedAtDesc(buildingId);
        }
        return noticeRepo.findAllByOrderByPublishedAtDesc();
    }

    @PostMapping
    public BuildingNotice create(@RequestBody BuildingNotice notice, Authentication auth) {
        User publisher = authHelper.requireUser(auth);
        notice.setId(null);
        notice.setPublisherName(publisher.getRealName());
        notice.setPublishedAt(LocalDateTime.now());
        notice.setStatus(BuildingNotice.NoticeStatus.PUBLISHED);
        return noticeRepo.save(notice);
    }

    @PutMapping("/{id}/revoke")
    public BuildingNotice revoke(@PathVariable Long id) {
        BuildingNotice notice = noticeRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("公告不存在"));
        notice.setStatus(BuildingNotice.NoticeStatus.REVOKED);
        return noticeRepo.save(notice);
    }
}
