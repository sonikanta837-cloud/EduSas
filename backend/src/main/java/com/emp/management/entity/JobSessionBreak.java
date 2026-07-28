package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "job_session_breaks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobSessionBreak {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_work_session_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private JobWorkSession jobWorkSession;

    @Column(nullable = false)
    private LocalDateTime breakStartTime;

    private LocalDateTime breakEndTime;

    // Minutes on break — populated when the break ends
    private Integer breakMinutes;

    // Guards the >60-minute break-duration alert email for this specific break —
    // set once the alert fires so it is never sent twice for the same break.
    @Column(nullable = false)
    @Builder.Default
    private boolean alertSent = false;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
