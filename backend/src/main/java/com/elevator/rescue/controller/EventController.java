package com.elevator.rescue.controller;

import com.elevator.rescue.dto.EventDtos.*;
import com.elevator.rescue.entity.RescueEvent;
import com.elevator.rescue.entity.User;
import com.elevator.rescue.repository.RescueEventRepository;
import com.elevator.rescue.security.AuthHelper;
import com.elevator.rescue.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final RescueEventRepository eventRepo;
    private final AuthHelper authHelper;

    @GetMapping
    public List<RescueEvent> list(@RequestParam(required = false) String status,
                                  @RequestParam(required = false) Long elevatorId,
                                  @RequestParam(required = false) Long buildingId) {
        Stream<RescueEvent> stream = eventRepo.findAllByOrderByAlarmTimeDesc().stream();
        if (status != null && !status.isBlank()) {
            if ("ACTIVE".equals(status)) {
                stream = stream.filter(e -> e.getStatus() != RescueEvent.EventStatus.CLOSED);
            } else {
                RescueEvent.EventStatus s = RescueEvent.EventStatus.valueOf(status);
                stream = stream.filter(e -> e.getStatus() == s);
            }
        }
        if (elevatorId != null) {
            stream = stream.filter(e -> e.getElevator().getId().equals(elevatorId));
        }
        if (buildingId != null) {
            stream = stream.filter(e -> e.getBuilding().getId().equals(buildingId));
        }
        return stream.toList();
    }

    @PostMapping
    public RescueEvent create(@Valid @RequestBody EventCreateRequest req, Authentication auth) {
        return eventService.create(req, authHelper.requireUser(auth));
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        return eventService.getDetail(id);
    }

    @PostMapping("/{id}/dispatch")
    public RescueEvent dispatch(@PathVariable Long id, @RequestBody DispatchRequest req, Authentication auth) {
        return eventService.dispatch(id, req, authHelper.requireUser(auth));
    }

    @PostMapping("/{id}/calls")
    public RescueEvent addCall(@PathVariable Long id, @RequestBody CallRequest req, Authentication auth) {
        return eventService.addCall(id, req, authHelper.requireUser(auth));
    }

    @PostMapping("/{id}/arrive")
    public RescueEvent arrive(@PathVariable Long id, @Valid @RequestBody ArriveRequest req, Authentication auth) {
        return eventService.arrive(id, req, authHelper.requireUser(auth));
    }

    @PostMapping("/{id}/release")
    public RescueEvent release(@PathVariable Long id, @RequestBody ReleaseRequest req, Authentication auth) {
        return eventService.release(id, req, authHelper.requireUser(auth));
    }

    @PostMapping("/{id}/reset")
    public RescueEvent reset(@PathVariable Long id, @RequestBody ResetRequest req, Authentication auth) {
        return eventService.reset(id, req, authHelper.requireUser(auth));
    }

    @PostMapping("/{id}/close")
    public RescueEvent close(@PathVariable Long id, @Valid @RequestBody CloseRequest req, Authentication auth) {
        return eventService.close(id, req, authHelper.requireUser(auth));
    }

    @PutMapping("/{id}/flags")
    public RescueEvent updateFlags(@PathVariable Long id, @RequestBody FlagsRequest req, Authentication auth) {
        return eventService.updateFlags(id, req, authHelper.requireUser(auth));
    }

    @PutMapping("/{id}/notifications/{notificationId}")
    public void updateNotification(@PathVariable Long id, @PathVariable Long notificationId,
                                   @Valid @RequestBody NotificationUpdateRequest req, Authentication auth) {
        eventService.updateNotification(id, notificationId, req.status(), authHelper.requireUser(auth));
    }

    @PostMapping("/{id}/followups")
    public RescueEvent addFollowup(@PathVariable Long id, @RequestBody FollowupRequest req, Authentication auth) {
        return eventService.addFollowup(id, req, authHelper.requireUser(auth));
    }

    @PutMapping("/{id}/rectification")
    public RescueEvent updateRectification(@PathVariable Long id, @Valid @RequestBody RectificationRequest req,
                                           Authentication auth) {
        return eventService.updateRectification(id, req, authHelper.requireUser(auth));
    }
}
