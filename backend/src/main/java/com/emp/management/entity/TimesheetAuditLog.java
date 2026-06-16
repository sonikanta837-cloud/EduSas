package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "timesheet_audit_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimesheetAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private EmployeeDetails employee;

    private Long correctionRequestId;

    @Column(nullable = false)
    private LocalDate workDate;

    private LocalTime originalLogoutTime;
    private LocalTime requestedLogoutTime;
    private LocalTime approvedLogoutTime;

    private String approverName;

    @Column(nullable = false)
    private String action; // SUBMITTED | APPROVED | REJECTED

    @Column(nullable = false)
    private LocalDateTime actionDateTime;

    @Column(length = 1000)
    private String remarks;
}
