package com.emp.management.controller;

import com.emp.management.dto.PerformanceReviewDTO;
import com.emp.management.service.PerformanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/performance")
@RequiredArgsConstructor
public class PerformanceController {

    private final PerformanceService performanceService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'ASSISTANT_MANAGER')")
    public ResponseEntity<PerformanceReviewDTO> createReview(@RequestBody PerformanceReviewDTO dto) {
        return ResponseEntity.ok(performanceService.createReview(dto));
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<PerformanceReviewDTO>> getEmployeeReviews(@PathVariable Long employeeId) {
        return ResponseEntity.ok(performanceService.getEmployeeReviews(employeeId));
    }

    @GetMapping("/reviewer/{reviewerId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'ASSISTANT_MANAGER')")
    public ResponseEntity<List<PerformanceReviewDTO>> getReviewsByReviewer(@PathVariable Long reviewerId) {
        return ResponseEntity.ok(performanceService.getReviewsByReviewer(reviewerId));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PerformanceReviewDTO>> getAllReviews() {
        return ResponseEntity.ok(performanceService.getAllReviews());
    }

    @GetMapping("/employee/{employeeId}/average")
    public ResponseEntity<Map<String, Object>> getAverageRating(@PathVariable Long employeeId) {
        Double avg = performanceService.getAverageRating(employeeId);
        return ResponseEntity.ok(Map.of("employeeId", employeeId, "averageRating", avg != null ? avg : 0.0));
    }
}
