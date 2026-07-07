package com.emp.management.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AtsCandidateDTO {

    private Long id;
    private String candidateId;
    private String name;
    private String email;
    private String phone;
    private String address;
    private String addressStreet;
    private String addressArea;
    private String addressLandmark;
    private String addressCity;
    private String addressDistrict;
    private String addressState;
    private String addressPostalCode;
    private String addressCountry;
    private String appliedProfile;
    private String officeLocation;
    private String source;

    // ── Parsed resume fields ──────────────────────────────────────────────────
    private String skills;
    private String totalExperienceYears;
    private String currentDesignation;
    private String currentCompanyCv;
    private String educationSummary;
    private String linkedinUrl;
    private String githubUrl;

    // ── File ──────────────────────────────────────────────────────────────────
    private String resumeOriginalName;
    private LocalDateTime resumeUploadedAt;
    private String status;

    private Long createdById;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Stage details (populated in getCandidate)
    private AtsHrScreeningDTO hrScreening;
    private List<AtsTechnicalInterviewDTO> technicalInterviews;
    private AtsFinalRoundDTO finalRound;
}
