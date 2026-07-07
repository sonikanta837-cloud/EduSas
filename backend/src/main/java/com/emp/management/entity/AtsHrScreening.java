package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "ats_hr_screenings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AtsHrScreening {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private AtsCandidate candidate;

    @Column(name = "communication_skills")
    private String communicationSkills;

    @Column(name = "current_company")
    private String currentCompany;

    @Column(name = "current_ctc")
    private String currentCtc;

    @Column(name = "expected_ctc")
    private String expectedCtc;

    @Column(name = "notice_period")
    private String noticePeriod;

    @Column(name = "total_experience")
    private String totalExperience;

    @Column(name = "relevant_experience")
    private String relevantExperience;

    @Column(name = "preferred_location")
    private String preferredLocation;

    private String availability;

    @Column(name = "reason_for_change", columnDefinition = "TEXT")
    private String reasonForChange;

    @Column(name = "current_role_responsibilities", columnDefinition = "TEXT")
    private String currentRoleResponsibilities;

    @Column(name = "work_base", length = 100)
    private String workBase;

    @Column(name = "screening_date")
    private LocalDate screeningDate;

    @Column(name = "hr_comments", columnDefinition = "TEXT")
    private String hrComments;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(length = 20)
    private String decision; // PENDING / SUITABLE / NOT_SUITABLE

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conducted_by_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private EmployeeDetails conductedBy;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
