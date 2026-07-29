package com.emp.management.dto;

import com.emp.management.entity.CorrectionReason;
import com.emp.management.entity.CorrectionRequestStatus;
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
public class WorkingHoursCorrectionRequestDTO {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private String employeeCode;
    private String department;
    private String managerName;
    private LocalDate workDate;

    private CorrectionReason reason;
    private String reasonComments;
    private LocalDateTime requestedBreakStartTime;
    private LocalDateTime requestedBreakEndTime;
    private String overtimeRemarks;

    private Integer originalWorkingMinutes;
    private Integer originalBreakMinutes;
    private Integer originalOfficeMinutes;
    private Integer originalOvertimeMinutes;
    private String originalStatus;

    private Integer requestedWorkingMinutes;
    private Integer requestedBreakMinutes;
    private Integer requestedOfficeMinutes;
    private Integer requestedOvertimeMinutes;
    private String requestedStatus;

    private Integer finalWorkingMinutes;
    private Integer finalBreakMinutes;
    private Integer finalOfficeMinutes;
    private Integer finalOvertimeMinutes;
    private String finalStatus;

    private CorrectionRequestStatus status;
    private String approvedByName;
    private String managerComment;
    private LocalDateTime submittedAt;
    private LocalDateTime actionDate;
}
