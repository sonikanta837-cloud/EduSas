package com.emp.management.controller;

import com.emp.management.dto.JobSessionLoginRequest;
import com.emp.management.dto.JobSessionTodayDTO;
import com.emp.management.dto.JobWorkSessionDTO;
import com.emp.management.dto.WorkReportDTO;
import com.emp.management.entity.EmployeeDetails;
import com.emp.management.entity.Role;
import com.emp.management.repository.EmployeeDetailsRepository;
import com.emp.management.service.JobWorkSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/job-sessions")
@RequiredArgsConstructor
public class JobWorkSessionController {

    private final JobWorkSessionService jobWorkSessionService;
    private final EmployeeDetailsRepository employeeDetailsRepository;

    @PostMapping("/login")
    public ResponseEntity<JobWorkSessionDTO> login(@RequestBody @Valid JobSessionLoginRequest req,
                                                     Authentication authentication) {
        requireSelfOrAdmin(req.getEmployeeId(), authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(jobWorkSessionService.login(req));
    }

    @PostMapping("/logout/{employeeId}")
    public ResponseEntity<JobWorkSessionDTO> logout(@PathVariable Long employeeId, Authentication authentication) {
        requireSelfOrAdmin(employeeId, authentication);
        return ResponseEntity.ok(jobWorkSessionService.logout(employeeId));
    }

    // Atomically closes the current open session and opens a new one — used by the
    // "Switch Job" UI action so there's never a gap (or a partial failure leaving no
    // open session) between ending one job and starting the next.
    @PostMapping("/switch")
    public ResponseEntity<JobWorkSessionDTO> switchJob(@RequestBody @Valid JobSessionLoginRequest req,
                                                         Authentication authentication) {
        requireSelfOrAdmin(req.getEmployeeId(), authentication);
        return ResponseEntity.ok(jobWorkSessionService.switchJob(req));
    }

    @PostMapping("/break/start/{employeeId}")
    public ResponseEntity<JobWorkSessionDTO> startBreak(@PathVariable Long employeeId, Authentication authentication) {
        requireSelfOrAdmin(employeeId, authentication);
        return ResponseEntity.ok(jobWorkSessionService.startBreak(employeeId));
    }

    @PostMapping("/break/end/{employeeId}")
    public ResponseEntity<JobWorkSessionDTO> endBreak(@PathVariable Long employeeId, Authentication authentication) {
        requireSelfOrAdmin(employeeId, authentication);
        return ResponseEntity.ok(jobWorkSessionService.endBreak(employeeId));
    }

    @GetMapping("/today/{employeeId}")
    public ResponseEntity<JobSessionTodayDTO> getToday(@PathVariable Long employeeId, Authentication authentication) {
        requireSelfOrPrivileged(employeeId, authentication);
        return ResponseEntity.ok(jobWorkSessionService.getToday(employeeId));
    }

    @GetMapping("/range/{employeeId}")
    public ResponseEntity<List<JobWorkSessionDTO>> getRange(
            @PathVariable Long employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            Authentication authentication) {
        requireSelfOrPrivileged(employeeId, authentication);
        return ResponseEntity.ok(jobWorkSessionService.getRange(employeeId, start, end));
    }

    @GetMapping("/work-report")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR', 'MANAGER', 'ASSISTANT_MANAGER')")
    public ResponseEntity<List<WorkReportDTO>> getWorkReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ResponseEntity.ok(jobWorkSessionService.getWorkReport(start, end));
    }

    private void requireSelfOrAdmin(Long targetId, Authentication auth) {
        EmployeeDetails caller = employeeDetailsRepository.findByUserEmail(auth.getName()).orElse(null);
        if (caller == null) return;
        Role role = caller.getUser() != null ? caller.getUser().getRole() : null;
        if (role != Role.ADMIN && role != Role.DIRECTOR && !caller.getId().equals(targetId)) {
            throw new AccessDeniedException("Access denied");
        }
    }

    private void requireSelfOrPrivileged(Long targetId, Authentication auth) {
        EmployeeDetails caller = employeeDetailsRepository.findByUserEmail(auth.getName()).orElse(null);
        if (caller == null) return;
        Role role = caller.getUser() != null ? caller.getUser().getRole() : null;
        boolean privileged = role == Role.ADMIN || role == Role.DIRECTOR || role == Role.HR
                          || role == Role.MANAGER || role == Role.ASSISTANT_MANAGER;
        if (!privileged && !caller.getId().equals(targetId)) {
            throw new AccessDeniedException("Access denied");
        }
    }
}
