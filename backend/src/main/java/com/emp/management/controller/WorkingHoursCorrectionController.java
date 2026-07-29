package com.emp.management.controller;

import com.emp.management.dto.CorrectionAuditLogDTO;
import com.emp.management.dto.WorkingHoursCorrectionRequestDTO;
import com.emp.management.dto.WorkingHoursCorrectionSubmitRequest;
import com.emp.management.entity.CorrectionRequestStatus;
import com.emp.management.exception.BadRequestException;
import com.emp.management.service.WorkingHoursCorrectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/working-hours-corrections")
@RequiredArgsConstructor
public class WorkingHoursCorrectionController {

    private final WorkingHoursCorrectionService correctionService;

    @PostMapping("/apply/{employeeId}")
    public ResponseEntity<WorkingHoursCorrectionRequestDTO> apply(@PathVariable Long employeeId,
                                                                    @RequestBody @Valid WorkingHoursCorrectionSubmitRequest req,
                                                                    Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(correctionService.submit(employeeId, req, authentication.getName()));
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<WorkingHoursCorrectionRequestDTO>> getForEmployee(@PathVariable Long employeeId,
                                                                                   Authentication authentication) {
        return ResponseEntity.ok(correctionService.getMyRequests(employeeId, authentication.getName()));
    }

    @GetMapping("/manager/{managerId}/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'MANAGER', 'ASSISTANT_MANAGER', 'HR')")
    public ResponseEntity<List<WorkingHoursCorrectionRequestDTO>> getPendingForManager(@PathVariable Long managerId) {
        return ResponseEntity.ok(correctionService.getPendingForManager(managerId));
    }

    @GetMapping("/manager/{managerId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'MANAGER', 'ASSISTANT_MANAGER', 'HR')")
    public ResponseEntity<List<WorkingHoursCorrectionRequestDTO>> getAllForManager(@PathVariable Long managerId) {
        return ResponseEntity.ok(correctionService.getAllForManager(managerId));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'DIRECTOR')")
    public ResponseEntity<List<WorkingHoursCorrectionRequestDTO>> getAll() {
        return ResponseEntity.ok(correctionService.getAll());
    }

    @PatchMapping("/{requestId}/action")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'MANAGER', 'ASSISTANT_MANAGER')")
    public ResponseEntity<WorkingHoursCorrectionRequestDTO> processRequest(@PathVariable Long requestId,
                                                                             @RequestBody Map<String, String> body,
                                                                             Authentication authentication) {
        String rawStatus = body.get("status");
        if (rawStatus == null || rawStatus.isBlank()) {
            throw new BadRequestException("status is required");
        }
        CorrectionRequestStatus status;
        try {
            status = CorrectionRequestStatus.valueOf(rawStatus.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status: " + rawStatus);
        }
        String comment = body.getOrDefault("comment", "");
        return ResponseEntity.ok(correctionService.decide(requestId, authentication.getName(), status, comment));
    }

    @GetMapping("/{requestId}/audit-trail")
    public ResponseEntity<List<CorrectionAuditLogDTO>> getAuditTrail(@PathVariable Long requestId,
                                                                       Authentication authentication) {
        return ResponseEntity.ok(correctionService.getAuditTrail(requestId, authentication.getName()));
    }
}
