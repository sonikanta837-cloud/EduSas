package com.emp.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipDTO {
    private Long id;

    // Employee under PIP
    private Long employeeId;
    private String employeeName;
    private String employeeDepartment;
    private String employeePosition;

    // Creator (Manager / HR / Admin)
    private Long createdById;
    private String createdByName;

    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;
    private String improvementAreas;
    private String supportProvided;
    private String consequences;

    private String status; // ACTIVE | COMPLETED | EXTENDED | TERMINATED
    private String finalNotes;
    private LocalDate outcomeDate;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Aggregated stats (always populated)
    private int totalGoals;
    private int achievedGoals;
    private int overallProgressPercent;
    private int weeklyReviewCount;
    private int commentCount;
    private boolean overdue;

    // Nested detail — null in list responses, populated in detail response
    private List<PipGoalDTO> goals;
    private List<PipWeeklyReviewDTO> weeklyReviews;
    private List<PipCommentDTO> comments;
    private List<PipAuditLogDTO> auditLogs;
}
