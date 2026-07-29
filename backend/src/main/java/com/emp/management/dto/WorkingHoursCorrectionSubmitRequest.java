package com.emp.management.dto;

import com.emp.management.entity.CorrectionReason;
import jakarta.validation.constraints.NotNull;
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
public class WorkingHoursCorrectionSubmitRequest {
    @NotNull(message = "Work date is required")
    private LocalDate workDate;

    @NotNull(message = "Reason is required")
    private CorrectionReason reason;

    private String reasonComments;
    private LocalDateTime requestedBreakStartTime;
    private LocalDateTime requestedBreakEndTime;
    private String overtimeRemarks;
}
