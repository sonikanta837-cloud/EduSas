package com.emp.management.dto;

import lombok.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerformanceExportRowDTO {
    private String employeeName;
    private String employeeCode;
    private String department;
    private String reviewerName;
    private String reviewerRole;
    private String reviewPeriod;
    private LocalDate reviewDate;
    private Integer rating;
    private String ratingLabel;
    private boolean belowThreshold;
    private Double employeeAverageRating;
    private boolean activePip;
    private String pipTitle;
    private String pipStatus;
    private LocalDate pipStartDate;
    private LocalDate pipEndDate;
    private String pipReason;
    private Long pipGoalsAchieved;
    private Long pipGoalsTotal;
    private String comments;
    private String strengths;
    private String areasOfImprovement;
}
