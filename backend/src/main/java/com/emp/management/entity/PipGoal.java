package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "pip_goals")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pip_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private PerformanceImprovementPlan pip;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String successCriteria;

    @Builder.Default
    private int progressPercent = 0;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PipGoalStatus status = PipGoalStatus.NOT_STARTED;

    private LocalDate targetDate;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
