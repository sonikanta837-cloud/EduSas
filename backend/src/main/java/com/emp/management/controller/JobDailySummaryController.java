package com.emp.management.controller;

import com.emp.management.dto.JobDailySummaryDTO;
import com.emp.management.entity.EmployeeDetails;
import com.emp.management.entity.Role;
import com.emp.management.repository.EmployeeDetailsRepository;
import com.emp.management.service.JobDailySummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/job-summaries")
@RequiredArgsConstructor
public class JobDailySummaryController {

    private final JobDailySummaryService jobDailySummaryService;
    private final EmployeeDetailsRepository employeeDetailsRepository;

    @GetMapping("/my")
    public ResponseEntity<List<JobDailySummaryDTO>> getMy(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            Authentication authentication) {
        EmployeeDetails me = employeeDetailsRepository.findByUserEmail(authentication.getName())
                .orElseThrow(() -> new com.emp.management.exception.ResourceNotFoundException("Employee profile not found"));
        return ResponseEntity.ok(jobDailySummaryService.getRange(me.getId(), start, end));
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','HR','MANAGER','ASSISTANT_MANAGER')")
    public ResponseEntity<List<JobDailySummaryDTO>> getForEmployee(
            @PathVariable Long employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ResponseEntity.ok(jobDailySummaryService.getRange(employeeId, start, end));
    }

    // Team/org-wide attendance report — ADMIN/DIRECTOR/HR see everyone, managers see their direct reports
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','HR','MANAGER','ASSISTANT_MANAGER')")
    public ResponseEntity<List<JobDailySummaryDTO>> getTeam(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            Authentication authentication) {
        return ResponseEntity.ok(jobDailySummaryService.getTeamRange(authentication.getName(), start, end));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','HR','MANAGER','ASSISTANT_MANAGER')")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            Authentication authentication) {
        byte[] csv = jobDailySummaryService.exportToCsv(authentication.getName(), start, end);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=attendance-report.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    @PostMapping("/generate/range")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','HR')")
    public ResponseEntity<Void> generateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        jobDailySummaryService.generateAllForRange(start, end);
        return ResponseEntity.ok().build();
    }
}
