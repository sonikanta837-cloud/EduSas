package com.emp.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeParseResponse {

    // ── Contact ───────────────────────────────────────────────────────────────
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String personalEmail;

    // ── Personal ──────────────────────────────────────────────────────────────
    private String dateOfBirth;    // normalized to YYYY-MM-DD when possible
    private String gender;
    private String maritalStatus;

    // ── Professional ──────────────────────────────────────────────────────────
    private String position;           // current designation / job title
    private String totalExperience;    // e.g. "5 years"
    private String currentExperience;  // duration at current employer
    private String skills;
    private String experience;         // work history summary (falls back to summary)
    private String summary;            // professional summary / objective (raw)

    // ── Location / Address ────────────────────────────────────────────────────
    private String seatingLocation;
    private String presentAddress;

    // ── Parse metadata ────────────────────────────────────────────────────────
    private int          confidenceScore; // 0–100
    private List<String> parsedFields;    // form field keys auto-populated
    private List<String> reviewFields;    // form field keys needing manual attention
    private List<String> reviewMessages;  // human-readable review prompts for the banner
}
