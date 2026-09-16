package com.elevator.rescue.repository;

import com.elevator.rescue.entity.BuildingNotice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BuildingNoticeRepository extends JpaRepository<BuildingNotice, Long> {
    List<BuildingNotice> findByBuildingIdOrderByPublishedAtDesc(Long buildingId);

    List<BuildingNotice> findByEventIdOrderByPublishedAtDesc(Long eventId);

    List<BuildingNotice> findAllByOrderByPublishedAtDesc();

    List<BuildingNotice> findByStatusOrderByPublishedAtDesc(BuildingNotice.NoticeStatus status);

    List<BuildingNotice> findByBuildingIdAndStatusOrderByPublishedAtDesc(Long buildingId, BuildingNotice.NoticeStatus status);

    /** 同一整改单下指定类型与状态的公告（用于复检通过时结束关联停梯公告） */
    List<BuildingNotice> findByRectificationPlanIdAndTypeAndStatus(Long rectificationPlanId,
                                                                   BuildingNotice.NoticeType type,
                                                                   BuildingNotice.NoticeStatus status);

    /** 同一整改单的公告时间线（停梯 → 恢复，含已撤回，可审计） */
    List<BuildingNotice> findByRectificationPlanIdOrderByPublishedAtAsc(Long rectificationPlanId);
}
