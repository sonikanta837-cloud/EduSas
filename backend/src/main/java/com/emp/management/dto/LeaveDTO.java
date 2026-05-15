package com.emp.management.dto;

import com.emp.management.entity.LeaveStatus;
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
public class LeaveDTO {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private String department;
    private String managerName;
    private String location;
    private String leaveType;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer totalDays;
    private String reason;
    private LeaveStatus status;
    private String managerComment;
    private String approvedByName;
    private LocalDateTime appliedAt;
}
