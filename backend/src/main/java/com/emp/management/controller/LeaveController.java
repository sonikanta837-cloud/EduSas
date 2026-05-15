package com.emp.management.controller;

import com.emp.management.dto.LeaveDTO;
import com.emp.management.entity.LeaveStatus;
import com.emp.management.service.LeaveService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/leaves")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;

    @PostMapping("/apply/{employeeId}")
    public ResponseEntity<LeaveDTO> applyLeave(@PathVariable Long employeeId,
                                                @RequestBody LeaveDTO dto) {
        return ResponseEntity.ok(leaveService.applyLeave(employeeId, dto));
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<LeaveDTO>> getMyLeaves(@PathVariable Long employeeId) {
        return ResponseEntity.ok(leaveService.getMyLeaves(employeeId));
    }

    @GetMapping("/manager/{managerId}/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'ASSISTANT_MANAGER')")
    public ResponseEntity<List<LeaveDTO>> getPendingForManager(@PathVariable Long managerId) {
        return ResponseEntity.ok(leaveService.getPendingLeavesForManager(managerId));
    }

    @GetMapping("/manager/{managerId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'ASSISTANT_MANAGER')")
    public ResponseEntity<List<LeaveDTO>> getAllForManager(@PathVariable Long managerId) {
        return ResponseEntity.ok(leaveService.getLeavesForManager(managerId));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<LeaveDTO>> getAllLeaves() {
        return ResponseEntity.ok(leaveService.getAllLeaves());
    }

    @PatchMapping("/{leaveId}/action")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'ASSISTANT_MANAGER')")
    public ResponseEntity<LeaveDTO> processLeave(@PathVariable Long leaveId,
                                                   @RequestBody Map<String, String> body,
                                                   @RequestParam Long managerId) {
        LeaveStatus status = LeaveStatus.valueOf(body.get("status").toUpperCase());
        String comment = body.getOrDefault("comment", "");
        return ResponseEntity.ok(leaveService.updateLeaveStatus(leaveId, managerId, status, comment));
    }

    @PutMapping("/{leaveId}")
    public ResponseEntity<LeaveDTO> updateLeave(@PathVariable Long leaveId,
                                                 @RequestParam Long employeeId,
                                                 @RequestBody LeaveDTO dto) {
        return ResponseEntity.ok(leaveService.updateLeave(leaveId, employeeId, dto));
    }

    @DeleteMapping("/{leaveId}")
    public ResponseEntity<Void> deleteLeave(@PathVariable Long leaveId) {
        leaveService.deleteLeave(leaveId);
        return ResponseEntity.noContent().build();
    }
}
