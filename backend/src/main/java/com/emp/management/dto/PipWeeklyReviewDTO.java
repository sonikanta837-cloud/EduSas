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
public class PipWeeklyReviewDTO {
    private Long id;
    private Long pipId;
    private Long conductedById;
    private String conductedByName;
    private int weekNumber;
    private LocalDate reviewDate;
    private String overallProgress;
    private int progressRating; // 1–5
    private String achievements;
    private String challenges;
    private String actionItems;
    private LocalDateTime createdAt;
}
