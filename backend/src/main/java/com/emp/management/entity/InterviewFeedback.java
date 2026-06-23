package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "interview_feedback")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "round_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private InterviewRound round;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private EmployeeDetails submittedBy;

    private Integer overallRating;
    private Integer technicalRating;
    private Integer communicationRating;
    private Integer problemSolvingRating;
    private Integer cultureFitRating;

    @Column(columnDefinition = "TEXT")
    private String strengths;

    @Column(columnDefinition = "TEXT")
    private String weaknesses;

    @Column(columnDefinition = "TEXT")
    private String comments;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "VARCHAR(30)")
    private Recommendation recommendation;

    @Column(length = 10)
    private String projectFit;       // YES / MAYBE / NO

    @Column(length = 10)
    private String suspectedCheating; // YES / MAYBE / NO

    @CreationTimestamp
    private LocalDateTime submittedAt;
}
