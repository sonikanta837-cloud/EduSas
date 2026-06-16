package com.emp.management.controller;

import com.emp.management.dto.DailyWorkReportDTO;
import com.emp.management.service.DailyWorkReportService;
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
@RequestMapping("/api/work-reports")
@RequiredArgsConstructor
public class DailyWorkReportController {

    private final DailyWorkReportService reportService;

    // ── Team / All reports (Manager / HR / Admin) ────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR','MANAGER','ASSISTANT_MANAGER')")
    public ResponseEntity<List<DailyWorkReportDTO>> getTeamReports(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            Authentication auth) {
        return ResponseEntity.ok(reportService.getTeamReports(auth.getName(), start, end));
    }

    // ── My own reports (any role) ────────────────────────────────────────────

    @GetMapping("/my")
    public ResponseEntity<List<DailyWorkReportDTO>> getMyReports(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            Authentication auth) {
        return ResponseEntity.ok(reportService.getMyReports(auth.getName(), start, end));
    }

    // ── On-demand generation (Admin / HR) ────────────────────────────────────

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<Void> generateReports(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        reportService.generateAllReportsForDate(date);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/generate/range")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<Void> generateReportsForRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        reportService.generateAllReportsForRange(start, end);
        return ResponseEntity.ok().build();
    }

    // ── CSV export ────────────────────────────────────────────────────────────

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN','HR','MANAGER','ASSISTANT_MANAGER')")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            Authentication auth) {
        byte[] csv = reportService.exportToCsv(auth.getName(), start, end);
        String filename = "work-report-" + start + "-to-" + end + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }
}
