package com.emp.management.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewCandidateDTO {
    private Long id;
    private String name;
    private String email;
    private String phone;
    private String position;
    private String department;
    private String source;
    private String resumePath;
    private String notes;
    private String status;

    private Long createdById;
    private String createdByName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<InterviewRoundDTO> rounds;
    private int totalRounds;
    private int completedRounds;
    private Double averageRating;
    private String currentRoundType;
    private String currentRoundStatus;
    private String currentRoundInterviewerName;
    private LocalDateTime currentRoundScheduledAt;
}
