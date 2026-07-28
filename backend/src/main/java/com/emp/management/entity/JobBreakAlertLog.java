package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Immutable audit trail of break-duration alert emails sent — one row per
 * alert actually dispatched (distinct from {@link JobSessionBreak#isAlertSent()},
 * which is the mutable guard that prevents a second alert for the same break).
 * Field values are snapshotted at send time so the record stays meaningful even
 * if the underlying master data changes later.
 */
@Entity
@Table(name = "job_break_alert_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobBreakAlertLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private EmployeeDetails employee;

    @Column(nullable = false)
    private Long jobWorkSessionId;

    @Column(nullable = false)
    private Long jobSessionBreakId;

    private String employeeName;
    private String employeeCode;
    private String department;
    private String client;
    private String jobName;
    private String jobTask;

    @Column(nullable = false)
    private LocalDateTime breakStartTime;

    @Column(nullable = false)
    private Integer breakDurationMinutes;

    @Column(nullable = false)
    private String recipientEmails;

    @Column(nullable = false)
    private LocalDateTime sentAt;
}
