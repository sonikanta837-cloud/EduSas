package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "performance_export_audit_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerformanceExportAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private EmployeeDetails performedBy;

    @Column(nullable = false)
    private String format; // EXCEL | PDF | CSV

    @Column(nullable = false)
    private String scope; // ALL | FILTERED

    @Column(nullable = false)
    private Integer recordCount;

    @Column(columnDefinition = "TEXT")
    private String filterSummary;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
