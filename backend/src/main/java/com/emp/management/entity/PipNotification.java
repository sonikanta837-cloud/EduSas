package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "pip_notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private EmployeeDetails recipient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pip_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private PerformanceImprovementPlan pip;

    @Column(nullable = false)
    private String senderName;

    @Column(nullable = false)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
