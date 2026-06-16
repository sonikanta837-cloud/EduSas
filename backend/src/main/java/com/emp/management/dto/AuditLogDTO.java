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
public class AuditLogDTO {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private Long correctionRequestId;
    private LocalDate workDate;
    private LocalTime originalLogoutTime;
    private LocalTime requestedLogoutTime;
    private LocalTime approvedLogoutTime;
    private String approverName;
    private String action;
    private LocalDateTime actionDateTime;
    private String remarks;
}
