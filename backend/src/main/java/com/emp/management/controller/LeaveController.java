package com.emp.management.controller;

import com.emp.management.dto.LeaveDTO;
import com.emp.management.entity.LeaveStatus;
import com.emp.management.exception.BadRequestException;
import com.emp.management.repository.LeaveRepository;
import com.emp.management.service.LeaveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/leaves")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;
    private final LeaveRepository leaveRepository;

    @PostMapping("/apply/{employeeId}")
    public ResponseEntity<LeaveDTO> applyLeave(@PathVariable Long employeeId,
                                                @RequestBody @Valid LeaveDTO dto,
                                                Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(leaveService.applyLeave(employeeId, dto, authentication.getName()));
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<LeaveDTO>> getMyLeaves(@PathVariable Long employeeId,
                                                       Authentication authentication) {
        return ResponseEntity.ok(leaveService.getMyLeaves(employeeId, authentication.getName()));
    }

    @GetMapping("/manager/{managerId}/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'MANAGER', 'ASSISTANT_MANAGER')")
    public ResponseEntity<List<LeaveDTO>> getPendingForManager(@PathVariable Long managerId) {
        return ResponseEntity.ok(leaveService.getPendingLeavesForManager(managerId));
    }

    @GetMapping("/manager/{managerId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'MANAGER', 'ASSISTANT_MANAGER')")
    public ResponseEntity<List<LeaveDTO>> getAllForManager(@PathVariable Long managerId) {
        return ResponseEntity.ok(leaveService.getLeavesForManager(managerId));
    }

    @GetMapping("/public-holidays")
    public ResponseEntity<List<Map<String, String>>> getPublicHolidays() {
        List<Map<String, String>> result = leaveRepository.findDistinctPublicHolidays()
                .stream()
                .map(row -> {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("name", row[0] != null ? row[0].toString() : "");
                    m.put("date", row[1] != null ? row[1].toString() : "");
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public ResponseEntity<List<LeaveDTO>> getAllLeaves() {
        return ResponseEntity.ok(leaveService.getAllLeaves());
    }

    @PatchMapping("/{leaveId}/action")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'MANAGER', 'ASSISTANT_MANAGER')")
    public ResponseEntity<LeaveDTO> processLeave(@PathVariable Long leaveId,
                                                   @RequestBody Map<String, String> body,
                                                   Authentication authentication) {
        String rawStatus = body.get("status");
        if (rawStatus == null || rawStatus.isBlank()) {
            throw new BadRequestException("status is required");
        }
        LeaveStatus status;
        try {
            status = LeaveStatus.valueOf(rawStatus.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status: " + rawStatus);
        }
        String comment = body.getOrDefault("comment", "");
        return ResponseEntity.ok(leaveService.updateLeaveStatus(leaveId, authentication.getName(), status, comment));
    }

    @PutMapping("/{leaveId}")
    public ResponseEntity<LeaveDTO> updateLeave(@PathVariable Long leaveId,
                                                 @RequestParam Long employeeId,
                                                 @RequestBody @Valid LeaveDTO dto,
                                                 Authentication authentication) {
        return ResponseEntity.ok(leaveService.updateLeave(leaveId, employeeId, dto, authentication.getName()));
    }

    @DeleteMapping("/{leaveId}")
    public ResponseEntity<Void> deleteLeave(@PathVariable Long leaveId, Authentication authentication) {
        leaveService.deleteLeave(leaveId, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
