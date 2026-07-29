package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// Table name deliberately does NOT reuse "correction_notifications" — that table
// is a leftover from the now-deleted Timesheet Correction Request feature (removed
// in the "attendance module changes" commit) and still carries its old NOT NULL
// `read`/`type` columns with no defaults, which ddl-auto=update never drops. Reusing
// that name made Hibernate bolt this entity's columns onto the dead table instead of
// creating a fresh one, so every insert failed on the orphaned columns.
@Entity
@Table(name = "working_hours_correction_notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CorrectionNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private EmployeeDetails recipient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "correction_request_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private WorkingHoursCorrectionRequest correctionRequest;

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
