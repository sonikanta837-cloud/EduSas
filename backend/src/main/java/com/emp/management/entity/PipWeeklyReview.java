package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "pip_weekly_reviews")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipWeeklyReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pip_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private PerformanceImprovementPlan pip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conducted_by_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private EmployeeDetails conductedBy;

    private int weekNumber;
    private LocalDate reviewDate;

    @Column(columnDefinition = "TEXT")
    private String overallProgress;

    private int progressRating; // 1–5

    @Column(columnDefinition = "TEXT")
    private String achievements;

    @Column(columnDefinition = "TEXT")
    private String challenges;

    @Column(columnDefinition = "TEXT")
    private String actionItems;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
