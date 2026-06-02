package com.emp.management.dto;

import lombok.*;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkReportDTO {
    private Long     entryId;
    private Long     employeeId;
    private String   employeeCode;
    private String   employeeName;
    private String   department;
    private String   location;
    private String   managerName;
    private LocalDate date;
    private String   projectName;
    private String   taskName;
    private Double   hours;
}
