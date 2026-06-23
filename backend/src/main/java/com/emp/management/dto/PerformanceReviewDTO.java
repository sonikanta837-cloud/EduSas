package com.emp.management.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
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
    @NotNull(message = "Employee is required")
    private Long employeeId;
    private String employeeName;
    @NotNull(message = "Reviewer is required")
    private Long reviewerId;
    private String reviewerName;
    private String reviewerRole;
    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating cannot exceed 5")
    private Integer rating;
    @NotBlank(message = "Comments are required")
    private String comments;
    private String strengths;
    private String areasOfImprovement;
    @NotNull(message = "Review date is required")
    @PastOrPresent(message = "Review date cannot be in the future")
    private LocalDate reviewDate;
    @NotBlank(message = "Review period is required")
    private String reviewPeriod;
    private LocalDateTime createdAt;
}
