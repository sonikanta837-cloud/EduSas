package com.emp.management.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewRoundDTO {
    private Long id;
    private Long candidateId;
    private String candidateName;
    private String candidatePosition;
    private int roundNumber;
    private String roundType;
    private LocalDateTime scheduledAt;
    private Integer durationMinutes;
    private String location;
    private String status;

    private Long interviewerId;
    private String interviewerName;
    private String interviewerCode;
    private String interviewerEmail;

    private Long assignedById;
    private String assignedByName;

    private String managerNotes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<InterviewFeedbackDTO> feedbacks;
    private boolean feedbackSubmitted;
    private Double averageRating;
}
