package com.emp.management.dto;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AtsFinalRoundDTO {

    private Long id;
    private Long candidateId;
    private String candidateName;
    private String candidateEmail;

    // ── Video Interview ───────────────────────────────────────────────────────
    private String token;
    private String interviewStatus;
    private String interviewLink;
    private LocalDateTime scheduledAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime evaluatedAt;
    private String videoUrl;

    // ── Evaluation ────────────────────────────────────────────────────────────
    private Integer overallRating;
    private Integer communicationRating;
    private Integer cultureFitRating;
    private String  salaryDiscussion;
    private String  expectedCtc;
    private String  offeredCtc;
    private String  noticePeriod;
    private String  directorRemarks;

    // ── Director's interview notes ──────────────────────────────────────────────
    private LocalDate finalInterviewDate;
    private String    finalRemarks;
    private String    salaryRecommendation;
    private LocalDate joiningDate;

    // Director's advisory recommendation — PENDING / APPROVE / HOLD / REJECT
    private String        directorRecommendation;
    private LocalDateTime directorNotesAt;

    // ── HR's hiring decision — PENDING / APPROVE / HOLD / REJECT ────────────────
    private String finalDecision;

    private Long           decidedById;
    private String         decidedByName;
    private LocalDateTime  decidedAt;

    private Long   conductedById;
    private String conductedByName;

    private Long   assignedById;
    private String assignedByName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
