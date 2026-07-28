package com.emp.management.dto;

import lombok.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerformanceEmployeeSummaryRowDTO {
    private String employeeName;
    private String employeeCode;
    private String department;
    private String position;
    private int totalReviews;
    private Double averageRating;
    private Integer latestRating;
    private String latestRatingLabel;
    private LocalDate latestReviewDate;
    private String latestReviewerName;
    private boolean belowThreshold;
    private boolean activePip;
    private String pipTitle;
    private String pipStatus;
    private LocalDate pipStartDate;
    private LocalDate pipEndDate;
    private String pipReason;
    private Long pipGoalsAchieved;
    private Long pipGoalsTotal;
}
