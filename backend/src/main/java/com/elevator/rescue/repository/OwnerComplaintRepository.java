package com.elevator.rescue.repository;

import com.elevator.rescue.entity.OwnerComplaint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OwnerComplaintRepository extends JpaRepository<OwnerComplaint, Long> {
    List<OwnerComplaint> findByElevatorId(Long elevatorId);

    List<OwnerComplaint> findByEventId(Long eventId);

    List<OwnerComplaint> findAllByOrderByCreatedAtDesc();
}
