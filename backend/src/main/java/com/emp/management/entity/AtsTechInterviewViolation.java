package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ats_tech_interview_violations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AtsTechInterviewViolation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private AtsTechnicalInterview interview;

    @Column(name = "violation_type", length = 60)
    private String violationType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "violation_number")
    private Integer violationNumber;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @CreationTimestamp
    private LocalDateTime occurredAt;
}
