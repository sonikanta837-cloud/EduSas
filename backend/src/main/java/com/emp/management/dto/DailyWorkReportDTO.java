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
public class DailyWorkReportDTO {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private String employeeCode;
    private String department;
    private LocalDate reportDate;
    private LocalTime firstLoginTime;
    private LocalTime lastLogoutTime;
    private Integer totalOfficeMinutes;
    private Integer activeMinutes;
    private Integer breakMinutes;
    private Integer sessionCount;
    private String totalOfficeFormatted;
    private String activeFormatted;
    private String breakFormatted;
    private LocalDateTime generatedAt;
}
