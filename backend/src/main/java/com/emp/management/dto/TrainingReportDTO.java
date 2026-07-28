package com.emp.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingReportDTO {
    private Long enrollmentId;

    // Employee
    private Long employeeId;
    private String employeeName;
    private String employeeCode;
    private String department;
    private String designation;
    private String location;

    // Course
    private Long courseId;
    private String courseTitle;
    private String courseDescription;
    private Integer durationHours;
    private String youtubeUrl;
    private boolean courseActive;
    private String createdByName;
    private LocalDateTime courseCreatedAt;

    // Enrollment
    private String enrollmentStatus;
    private LocalDateTime enrolledAt;
    private LocalDateTime completionDate;
    private Integer examScore;

    // Exam
    private Integer passingScore;
    private Integer totalMarks;
    private String examResult; // "PASS" / "FAIL" / null

    // Certificate
    private String certificateNumber;
    private Integer certificateScore;
    private LocalDateTime certificateIssuedAt;
}
