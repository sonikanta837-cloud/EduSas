package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Immutable audit trail of under-working-hours alert emails — one row per
 * employee included in a sent consolidated manager email. Field values are
 * snapshotted at send time so the record stays meaningful even if the
 * underlying employee/session data changes later. Distinct from
 * {@link JobDailySummary#isUnderHoursAlertSent()}, which is the mutable guard
 * that prevents the same attendance date from being alerted twice.
 */
@Entity
@Table(name = "under_hours_alert_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnderHoursAlertLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private EmployeeDetails employee;

    @Column(nullable = false)
    private LocalDate workDate;

    private String employeeName;
    private String employeeCode;
    private String department;
    private String client;

    @Column(nullable = false)
    private Integer totalWorkingMinutes;

    @Column(nullable = false)
    private Integer totalBreakMinutes;

    @Column(nullable = false)
    private Integer expectedMinutes;

    @Column(nullable = false)
    private Integer shortfallMinutes;

    private LocalDateTime firstLoginTime;
    private LocalDateTime lastLogoutTime;

    private String attendanceStatus;

    @Column(nullable = false)
    private String recipientEmail;

    @Column(nullable = false)
    private LocalDateTime sentAt;
}
