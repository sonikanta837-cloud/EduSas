package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_work_sessions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "open_flag"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobWorkSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private EmployeeDetails employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private TimesheetMasterValue client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private TimesheetMasterValue job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_type_id", nullable = false)
    private TimesheetMasterValue jobType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_end_id", nullable = false)
    private TimesheetMasterValue periodEnd;

    @Column(nullable = false)
    private LocalDate workDate;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private LocalDateTime loginTime;

    private LocalDateTime logoutTime;

    // Minutes worked in this session — populated on logout
    private Integer sessionMinutes;

    // Guards the >60-minute break-alert email for the break that starts at this session's logout
    @Column(nullable = false)
    @Builder.Default
    private boolean breakAlertSent = false;

    // TRUE while this session is open, NULL once closed — lets a DB unique
    // constraint on (employee_id, open_flag) enforce "one open session per
    // employee" since MySQL treats multiple NULLs as non-conflicting.
    @Column(name = "open_flag")
    private Boolean openFlag;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobSessionStatus status;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
