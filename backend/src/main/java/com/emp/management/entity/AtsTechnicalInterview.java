package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ats_technical_interviews", indexes = {
    @Index(name = "idx_ats_tech_token", columnList = "token", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AtsTechnicalInterview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private AtsCandidate candidate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interviewer_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private EmployeeDetails interviewer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private EmployeeDetails assignedBy;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    // ── Video Interview fields ─────────────────────────────────────────────────

    @Column(length = 24, unique = true)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "interview_status", length = 25)
    @Builder.Default
    private AtsTechInterviewStatus interviewStatus = AtsTechInterviewStatus.PENDING_LINK;

    @Column(name = "interview_technology", length = 100)
    private String interviewTechnology;

    @Column(name = "question_count")
    @Builder.Default
    private Integer questionCount = 20;

    @Column(name = "duration_minutes")
    @Builder.Default
    private Integer durationMinutes = 45;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "evaluated_at")
    private LocalDateTime evaluatedAt;

    @Column(name = "video_url", length = 500)
    private String videoUrl;

    @Column(name = "violation_count")
    @Builder.Default
    private Integer violationCount = 0;

    @Column(name = "score")
    private Integer score;

    @Column(name = "total_marks")
    private Integer totalMarks;

    // WebRTC signaling — stored in DB for polling-based exchange
    @Column(name = "offer_sdp", columnDefinition = "LONGTEXT")
    private String offerSdp;

    @Column(name = "answer_sdp", columnDefinition = "LONGTEXT")
    private String answerSdp;

    // ── Evaluation ratings ────────────────────────────────────────────────────

    @Column(name = "technical_skills_rating")
    private Integer technicalSkillsRating;

    @Column(name = "communication_rating")
    private Integer communicationRating;

    @Column(name = "problem_solving_rating")
    private Integer problemSolvingRating;

    @Column(name = "coding_ability_rating")
    private Integer codingAbilityRating;

    @Column(name = "architecture_knowledge_rating")
    private Integer architectureKnowledgeRating;

    @Column(columnDefinition = "TEXT")
    private String comments;

    @Column(length = 10)
    private String decision; // PENDING / APPROVE / REJECT

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
