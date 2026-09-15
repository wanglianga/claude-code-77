package com.elevator.rescue.controller;

import com.elevator.rescue.entity.OwnerComplaint;
import com.elevator.rescue.entity.User;
import com.elevator.rescue.repository.OwnerComplaintRepository;
import com.elevator.rescue.security.AuthHelper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/complaints")
@RequiredArgsConstructor
public class ComplaintController {

    private final OwnerComplaintRepository complaintRepo;
    private final AuthHelper authHelper;

    public record HandleRequest(@NotNull(message = "请选择处理状态") String status, String result) {
    }

    @GetMapping
    public List<OwnerComplaint> list() {
        return complaintRepo.findAllByOrderByCreatedAtDesc();
    }

    @PostMapping
    public OwnerComplaint create(@RequestBody OwnerComplaint complaint) {
        complaint.setId(null);
        complaint.setStatus(OwnerComplaint.ComplaintStatus.PENDING);
        complaint.setCreatedAt(LocalDateTime.now());
        return complaintRepo.save(complaint);
    }

    @PutMapping("/{id}/handle")
    public OwnerComplaint handle(@PathVariable Long id, @Valid @RequestBody HandleRequest req, Authentication auth) {
        OwnerComplaint complaint = complaintRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("投诉不存在"));
        User operator = authHelper.requireUser(auth);
        complaint.setStatus(OwnerComplaint.ComplaintStatus.valueOf(req.status()));
        complaint.setResult(req.result());
        complaint.setHandledAt(LocalDateTime.now());
        complaint.setHandlerName(operator.getRealName());
        return complaintRepo.save(complaint);
    }
}
