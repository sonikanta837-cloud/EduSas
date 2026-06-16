package com.emp.management.dto;

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
public class PipGoalDTO {
    private Long id;
    private Long pipId;
    private String title;
    private String description;
    private String successCriteria;
    private int progressPercent;
    private String status; // NOT_STARTED | IN_PROGRESS | ACHIEVED | NOT_ACHIEVED
    private LocalDate targetDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
