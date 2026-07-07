package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "ats_candidates")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AtsCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "candidate_id", unique = true, nullable = false, length = 20)
    private String candidateId;

    @Column(nullable = false)
    private String name;

    private String email;
    private String phone;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(name = "address_street")
    private String addressStreet;

    @Column(name = "address_area")
    private String addressArea;

    @Column(name = "address_landmark")
    private String addressLandmark;

    @Column(name = "address_city")
    private String addressCity;

    @Column(name = "address_district")
    private String addressDistrict;

    @Column(name = "address_state")
    private String addressState;

    @Column(name = "address_postal_code", length = 20)
    private String addressPostalCode;

    @Column(name = "address_country", length = 100)
    private String addressCountry;

    @Column(name = "applied_profile", nullable = false)
    private String appliedProfile;

    @Column(name = "office_location")
    private String officeLocation;

    private String source;

    // ── Parsed resume fields ──────────────────────────────────────────────────

    @Column(columnDefinition = "TEXT")
    private String skills;

    @Column(name = "total_experience_years", length = 20)
    private String totalExperienceYears;

    @Column(name = "current_designation")
    private String currentDesignation;

    @Column(name = "current_company_cv")
    private String currentCompanyCv;

    @Column(name = "education_summary", columnDefinition = "TEXT")
    private String educationSummary;

    @Column(name = "linkedin_url")
    private String linkedinUrl;

    @Column(name = "github_url")
    private String githubUrl;

    @Column(name = "raw_resume_text", columnDefinition = "MEDIUMTEXT")
    private String rawResumeText;

    // ── File storage ──────────────────────────────────────────────────────────

    @Column(name = "resume_path")
    private String resumePath;

    @Column(name = "resume_original_name")
    private String resumeOriginalName;

    @Column(name = "resume_uploaded_at")
    private java.time.LocalDateTime resumeUploadedAt;

    @Column(nullable = false, length = 30)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private EmployeeDetails createdBy;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @OneToOne(mappedBy = "candidate", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private AtsHrScreening hrScreening;

    @OneToMany(mappedBy = "candidate", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<AtsTechnicalInterview> technicalInterviews;

    @OneToOne(mappedBy = "candidate", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private AtsFinalRound finalRound;
}
