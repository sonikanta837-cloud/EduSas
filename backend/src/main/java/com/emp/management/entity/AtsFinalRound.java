package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "ats_final_rounds", indexes = {
    @Index(name = "idx_ats_final_token", columnList = "token", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AtsFinalRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private AtsCandidate candidate;

    // ── Video Interview fields ────────────────────────────────────────────────

    @Column(length = 24, unique = true)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "interview_status", length = 25)
    @Builder.Default
    private AtsFinalInterviewStatus interviewStatus = AtsFinalInterviewStatus.PENDING_LINK;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "evaluated_at")
    private LocalDateTime evaluatedAt;

    @Column(name = "video_url", length = 500)
    private String videoUrl;

    @Column(name = "offer_sdp", columnDefinition = "LONGTEXT")
    private String offerSdp;

    @Column(name = "answer_sdp", columnDefinition = "LONGTEXT")
    private String answerSdp;

    // ── Evaluation ratings ────────────────────────────────────────────────────

    @Column(name = "overall_rating")
    private Integer overallRating;

    @Column(name = "communication_rating")
    private Integer communicationRating;

    @Column(name = "culture_fit_rating")
    private Integer cultureFitRating;

    @Column(name = "salary_discussion", columnDefinition = "TEXT")
    private String salaryDiscussion;

    @Column(name = "expected_ctc", length = 100)
    private String expectedCtc;

    @Column(name = "offered_ctc", length = 100)
    private String offeredCtc;

    @Column(name = "notice_period", length = 100)
    private String noticePeriod;

    @Column(name = "director_remarks", columnDefinition = "TEXT")
    private String directorRemarks;

    // ── Legacy / backward-compat fields ──────────────────────────────────────

    @Column(name = "final_interview_date")
    private LocalDate finalInterviewDate;

    @Column(name = "final_remarks", columnDefinition = "TEXT")
    private String finalRemarks;

    @Column(name = "salary_recommendation")
    private String salaryRecommendation;

    @Column(name = "joining_date")
    private LocalDate joiningDate;

    // Director's recommendation, submitted alongside their interview notes — advisory only.
    // The actual hiring outcome is `finalDecision`, recorded separately by HR.
    @Column(name = "director_recommendation", length = 10)
    @Builder.Default
    private String directorRecommendation = "PENDING"; // PENDING / APPROVE / HOLD / REJECT

    @Column(name = "director_notes_at")
    private LocalDateTime directorNotesAt;

    @Column(name = "final_decision", length = 10)
    @Builder.Default
    private String finalDecision = "PENDING"; // PENDING / APPROVE / HOLD / REJECT

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conducted_by_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private EmployeeDetails conductedBy;

    // HR/Admin who assigned this candidate to the Director above
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private EmployeeDetails assignedBy;

    // HR/Admin who recorded the final hiring decision (`finalDecision`)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private EmployeeDetails decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
