package com.emp.management.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    @NotNull(message = "Date is required")
    private LocalDate date;
    @NotBlank(message = "Project name is required")
    private String projectName;
    private String taskName;
    @NotNull(message = "Hours is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Hours must be greater than 0")
    @DecimalMax(value = "24.0", message = "Hours cannot exceed 24 per entry")
    private Double hours;
}
