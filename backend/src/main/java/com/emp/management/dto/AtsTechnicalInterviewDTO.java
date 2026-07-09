package com.emp.management.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AtsTechnicalInterviewDTO {

    private Long id;
    private Long candidateId;
    private String candidateName;
    private String candidateEmail;
    private String candidateAppliedProfile;
    private String candidateStatus;

    private Long interviewerId;
    private String interviewerName;

    private Long assignedById;
    private String assignedByName;

    private LocalDateTime scheduledAt;

    // ── Video Interview fields ──────────────────────────────────────────────────
    private String token;
    private String interviewStatus;  // AtsTechInterviewStatus name
    private String interviewLink;    // full frontend URL for candidate
    private String interviewTechnology;
    private String interviewDifficulty;
    private Integer questionCount;
    private Integer durationMinutes;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime evaluatedAt;
    private String videoUrl;
    private Integer violationCount;
    private Integer score;
    private Integer totalMarks;

    // Used when generating link
    private String technology;       // request field: which technology to pick questions from

    // ── Evaluation ratings (1–5) ────────────────────────────────────────────────
    private Integer technicalSkillsRating;
    private Integer communicationRating;
    private Integer problemSolvingRating;
    private Integer codingAbilityRating;
    private Integer architectureKnowledgeRating;

    private String comments;

    // PENDING / APPROVE / REJECT
    private String decision;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
