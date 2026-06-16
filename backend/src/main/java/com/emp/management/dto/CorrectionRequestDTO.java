package com.emp.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorrectionRequestDTO {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private String employeeCode;
    private String department;
    private Long sessionId;
    private LocalDate workDate;
    private LocalTime loginTime;
    private LocalTime firstLoginTime;
    private LocalTime requestedLogoutTime;
    private String reason;
    private String attachmentPath;
    private String status;
    private String resolverComment;
    private String resolvedBy;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
}
