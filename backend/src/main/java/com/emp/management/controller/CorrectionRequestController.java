package com.emp.management.controller;

import com.emp.management.dto.AttendanceSessionDTO;
import com.emp.management.dto.AuditLogDTO;
import com.emp.management.dto.CorrectionRequestDTO;
import com.emp.management.service.CorrectionRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/corrections")
@RequiredArgsConstructor
public class CorrectionRequestController {

    private final CorrectionRequestService correctionRequestService;

    // ── Employee: get sessions awaiting correction ───────────────────────────

    @GetMapping("/pending-sessions")
    @PreAuthorize("hasAnyRole('EMPLOYEE','HR','MANAGER','ASSISTANT_MANAGER')")
    public ResponseEntity<List<AttendanceSessionDTO>> getPendingSessions(Authentication auth) {
        return ResponseEntity.ok(correctionRequestService.getPendingSessions(auth.getName()));
    }

    // ── Employee: submit a correction request ────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE','HR','MANAGER','ASSISTANT_MANAGER')")
    public ResponseEntity<CorrectionRequestDTO> submit(
            @RequestParam Long sessionId,
            @RequestParam String requestedLogoutTime,
            @RequestParam String reason,
            @RequestParam(required = false) MultipartFile attachment,
            Authentication auth) {
        return ResponseEntity.ok(correctionRequestService.submit(
                auth.getName(), sessionId, requestedLogoutTime, reason, attachment));
    }

    // ── Employee: view own request history ───────────────────────────────────

    @GetMapping("/my")
    public ResponseEntity<List<CorrectionRequestDTO>> getMyRequests(Authentication auth) {
        return ResponseEntity.ok(correctionRequestService.getMyRequests(auth.getName()));
    }

    // ── Manager: pending requests for own team ───────────────────────────────

    @GetMapping("/pending/team")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','HR','MANAGER','ASSISTANT_MANAGER')")
    public ResponseEntity<List<CorrectionRequestDTO>> getPendingTeam(Authentication auth) {
        return ResponseEntity.ok(correctionRequestService.getPendingForTeam(auth.getName()));
    }

    // ── Admin / Director / HR: all pending requests ──────────────────────────

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','HR')")
    public ResponseEntity<List<CorrectionRequestDTO>> getAllPending() {
        return ResponseEntity.ok(correctionRequestService.getAllPending());
    }

    // ── Approve ──────────────────────────────────────────────────────────────

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','HR','MANAGER','ASSISTANT_MANAGER')")
    public ResponseEntity<CorrectionRequestDTO> approve(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        return ResponseEntity.ok(correctionRequestService.approve(
                id, auth.getName(),
                body.get("logoutTime"),
                body.getOrDefault("comment", "")));
    }

    // ── Reject ───────────────────────────────────────────────────────────────

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','HR','MANAGER','ASSISTANT_MANAGER')")
    public ResponseEntity<CorrectionRequestDTO> reject(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        return ResponseEntity.ok(correctionRequestService.reject(
                id, auth.getName(),
                body.getOrDefault("comment", "")));
    }

    // ── Audit log ────────────────────────────────────────────────────────────

    @GetMapping("/audit-logs")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','HR','MANAGER','ASSISTANT_MANAGER')")
    public ResponseEntity<List<AuditLogDTO>> getAuditLogs() {
        return ResponseEntity.ok(correctionRequestService.getAuditLogs());
    }
}
