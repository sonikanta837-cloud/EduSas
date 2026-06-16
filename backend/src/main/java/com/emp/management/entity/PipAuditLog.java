package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "pip_audit_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pip_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private PerformanceImprovementPlan pip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private EmployeeDetails performedBy;

    // PIP_CREATED | GOAL_ADDED | GOAL_UPDATED | GOAL_DELETED | REVIEW_ADDED | COMMENT_ADDED | STATUS_CHANGED | OUTCOME_SET | PIP_UPDATED
    private String action;

    @Column(columnDefinition = "TEXT")
    private String details;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
