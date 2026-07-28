package com.emp.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobSessionBreakDTO {
    private Long id;
    private LocalDateTime breakStartTime;
    private LocalDateTime breakEndTime;
    private Integer breakMinutes;
}
