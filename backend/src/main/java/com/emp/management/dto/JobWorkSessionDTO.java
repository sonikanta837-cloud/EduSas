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
public class JobWorkSessionDTO {
    private Long id;
    private Long employeeId;
    private TimesheetMasterValueDTO client;
    private TimesheetMasterValueDTO job;
    private TimesheetMasterValueDTO jobType;
    private TimesheetMasterValueDTO periodEnd;
    private LocalDate workDate;
    private String description;
    private LocalDateTime loginTime;
    private LocalDateTime logoutTime;
    private Integer sessionMinutes;
    private String status;
    private List<JobSessionBreakDTO> breaks;
    private boolean onBreak;
}
