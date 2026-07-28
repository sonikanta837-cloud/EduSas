package com.emp.management.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobSessionLoginRequest {
    @NotNull(message = "Employee is required")
    private Long employeeId;
    @NotNull(message = "Client is required")
    private Long clientId;
    @NotNull(message = "Job is required")
    private Long jobId;
    @NotNull(message = "Job type is required")
    private Long jobTypeId;
    @NotNull(message = "Period end is required")
    private Long periodEndId;
    private String description;
}
