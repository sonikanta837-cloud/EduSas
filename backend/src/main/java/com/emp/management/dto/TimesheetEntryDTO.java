package com.emp.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimesheetEntryDTO {
    private Long id;
    private Long employeeId;
    private LocalDate date;
    private String projectName;
    private String taskName;
    private Double hours;
}
