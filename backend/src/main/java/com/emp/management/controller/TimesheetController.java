package com.emp.management.controller;

import com.emp.management.dto.TimesheetEntryDTO;
import com.emp.management.dto.WorkReportDTO;
import com.emp.management.entity.EmployeeDetails;
import com.emp.management.entity.Role;
import com.emp.management.repository.EmployeeDetailsRepository;
import com.emp.management.service.TimesheetEntryService;
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
@RequestMapping("/api/timesheets")
@RequiredArgsConstructor
public class TimesheetController {

    private final TimesheetEntryService timesheetEntryService;
    private final EmployeeDetailsRepository employeeDetailsRepository;

    // ── Timesheet Entries (project-based hours) ────────────────────────────

    @GetMapping("/entries/employee/{empId}")
    public ResponseEntity<List<TimesheetEntryDTO>> getEntries(
            @PathVariable Long empId,
            @RequestParam int year,
            @RequestParam int month,
            Authentication authentication) {
        requireSelfOrPrivileged(empId, authentication);
        return ResponseEntity.ok(timesheetEntryService.getMonthlyEntries(empId, year, month));
    }

    @PostMapping("/entries")
    public ResponseEntity<TimesheetEntryDTO> saveEntry(@RequestBody @Valid TimesheetEntryDTO dto,
                                                        Authentication authentication) {
        TimesheetEntryDTO saved = timesheetEntryService.saveEntry(dto, authentication.getName());
        HttpStatus status = dto.getId() == null ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(saved);
    }

    @DeleteMapping("/entries/{id}")
    public ResponseEntity<Void> deleteEntry(@PathVariable Long id, Authentication authentication) {
        timesheetEntryService.deleteEntry(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/entries/project")
    public ResponseEntity<Void> deleteProjectRows(
            @RequestParam Long empId,
            @RequestParam String projectName,
            @RequestParam String taskName,
            Authentication authentication) {
        timesheetEntryService.deleteProjectRows(empId, projectName, taskName, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/work-report")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR', 'MANAGER', 'ASSISTANT_MANAGER')")
    public ResponseEntity<List<WorkReportDTO>> getWorkReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ResponseEntity.ok(timesheetEntryService.getWorkReport(start, end));
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
