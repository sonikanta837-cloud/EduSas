package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "working_hours_correction_audit_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkingHoursCorrectionAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "correction_request_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private WorkingHoursCorrectionRequest correctionRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private EmployeeDetails performedBy;

    // SUBMITTED | APPROVED | REJECTED
    private String action;

    @Column(columnDefinition = "TEXT")
    private String details;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
