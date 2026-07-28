package com.emp.management.controller;

import com.emp.management.dto.PerformanceExportAuditLogDTO;
import com.emp.management.dto.PerformanceReviewDTO;
import com.emp.management.service.PerformanceExportService;
import com.emp.management.service.PerformanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/performance")
@RequiredArgsConstructor
public class PerformanceController {

    private final PerformanceService performanceService;
    private final PerformanceExportService performanceExportService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'MANAGER', 'ASSISTANT_MANAGER')")
    public ResponseEntity<PerformanceReviewDTO> createReview(@Valid @RequestBody PerformanceReviewDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(performanceService.createReview(dto));
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<PerformanceReviewDTO>> getEmployeeReviews(@PathVariable Long employeeId,
                                                                          Authentication authentication) {
        return ResponseEntity.ok(performanceService.getEmployeeReviews(employeeId, authentication.getName()));
    }

    @GetMapping("/reviewer/{reviewerId}")
    public ResponseEntity<List<PerformanceReviewDTO>> getReviewsByReviewer(@PathVariable Long reviewerId,
                                                                            Authentication authentication) {
        return ResponseEntity.ok(performanceService.getReviewsByReviewer(reviewerId, authentication.getName()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR', 'MANAGER', 'ASSISTANT_MANAGER')")
    public ResponseEntity<List<PerformanceReviewDTO>> getAllReviews() {
        return ResponseEntity.ok(performanceService.getAllReviews());
    }

    @GetMapping("/employee/{employeeId}/average")
    public ResponseEntity<Map<String, Object>> getAverageRating(@PathVariable Long employeeId,
                                                                 Authentication authentication) {
        Double avg = performanceService.getAverageRating(employeeId, authentication.getName());
        return ResponseEntity.ok(Map.of("employeeId", employeeId, "averageRating", avg != null ? avg : 0.0));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'MANAGER', 'ASSISTANT_MANAGER')")
    public ResponseEntity<PerformanceReviewDTO> updateReview(@PathVariable Long id, @Valid @RequestBody PerformanceReviewDTO dto) {
        return ResponseEntity.ok(performanceService.updateReview(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'MANAGER', 'ASSISTANT_MANAGER')")
    public ResponseEntity<Void> deleteReview(@PathVariable Long id) {
        performanceService.deleteReview(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR')")
    public ResponseEntity<byte[]> exportReviews(
            @RequestParam String format,
            @RequestParam(defaultValue = "ALL") String scope,
            @RequestParam(defaultValue = "REVIEWS") String reportType,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String reviewPeriod,
            @RequestParam(required = false) Integer ratingMin,
            @RequestParam(required = false) String department,
            @RequestParam(defaultValue = "recent") String sortBy,
            @RequestParam(defaultValue = "false") boolean belowThresholdOnly,
            @RequestParam(defaultValue = "false") boolean pipOnly,
            Authentication authentication) throws IOException {

        byte[] data = performanceExportService.export(format, scope, reportType, search, reviewPeriod, ratingMin,
                department, sortBy, belowThresholdOnly, pipOnly, authentication.getName());

        String ext = "EXCEL".equalsIgnoreCase(format) ? "xlsx" : format.toLowerCase();
        String reportSegment = "EMPLOYEES".equalsIgnoreCase(reportType) ? "all-employees" : "reviews";
        String filename = "performance-" + reportSegment + "-" + scope.toLowerCase() + "-" + LocalDate.now() + "." + ext;
        MediaType mediaType = switch (format.toUpperCase()) {
            case "EXCEL" -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case "PDF" -> MediaType.APPLICATION_PDF;
            default -> MediaType.parseMediaType("text/csv");
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(mediaType)
                .body(data);
    }

    @GetMapping("/export/audit-log")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public ResponseEntity<List<PerformanceExportAuditLogDTO>> getExportAuditLog() {
        return ResponseEntity.ok(performanceExportService.getRecentAuditLogs());
    }
}
