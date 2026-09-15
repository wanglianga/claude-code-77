package com.elevator.rescue.repository;

import com.elevator.rescue.entity.BuildingNotice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BuildingNoticeRepository extends JpaRepository<BuildingNotice, Long> {
    List<BuildingNotice> findByBuildingIdOrderByPublishedAtDesc(Long buildingId);

    List<BuildingNotice> findByEventIdOrderByPublishedAtDesc(Long eventId);

    List<BuildingNotice> findAllByOrderByPublishedAtDesc();
}
