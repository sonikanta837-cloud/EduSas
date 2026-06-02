package com.emp.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceReviewDTO {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private Long reviewerId;
    private String reviewerName;
    private String reviewerRole;
    private Integer rating;
    private String comments;
    private String strengths;
    private String areasOfImprovement;
    private LocalDate reviewDate;
    private String reviewPeriod;
    private LocalDateTime createdAt;
}
