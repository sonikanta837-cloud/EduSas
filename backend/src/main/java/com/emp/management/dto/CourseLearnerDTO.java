package com.emp.management.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseLearnerDTO {
    private Long   employeeId;
    private String employeeName;
    private String employeeCode;
    private String status;            // ENROLLED | IN_PROGRESS | COMPLETED | FAILED
    private int    progressPercent;   // 0 / 60 / 100
    private String completionDate;    // null when not completed
    private String certificateNumber; // null when no cert
}
