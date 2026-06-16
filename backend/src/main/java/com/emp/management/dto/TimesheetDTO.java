package com.emp.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimesheetDTO {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private String department;
    private LocalDate workDate;
    private LocalTime loginTime;
    private LocalTime logoutTime;
    private Double workingHours;
    private String notes;
    private boolean alertSent;
    private boolean missingAlertSent;
    private boolean breakAlertSent;
}
