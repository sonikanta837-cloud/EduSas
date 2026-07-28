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
public class JobDailySummaryDTO {
    private Long employeeId;
    private String employeeName;
    private String department;
    private LocalDate workDate;
    private Integer totalWorkingMinutes;
    private Integer totalBreakMinutes;
    private Integer sessionCount;
    private LocalDateTime firstLoginTime;
    private LocalDateTime lastLogoutTime;
    private String primaryClient;
    private String status;
}
