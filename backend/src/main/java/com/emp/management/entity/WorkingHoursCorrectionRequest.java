package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "working_hours_correction_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkingHoursCorrectionRequest {

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

    // The specific session a break correction attaches to — resolved server-side,
    // null for reasons that don't touch raw session/break data.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_work_session_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private JobWorkSession jobWorkSession;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CorrectionReason reason;

    @Column(columnDefinition = "TEXT")
    private String reasonComments;

    // Only for FORGOT_START_BREAK / FORGOT_END_BREAK
    private LocalDateTime requestedBreakStartTime;
    private LocalDateTime requestedBreakEndTime;

    // Only for APPROVED_OVERTIME
    @Column(columnDefinition = "TEXT")
    private String overtimeRemarks;

    // Original values — snapshotted at submission time
    private Integer originalWorkingMinutes;
    private Integer originalBreakMinutes;
    private Integer originalOfficeMinutes;
    private Integer originalOvertimeMinutes;

    @Enumerated(EnumType.STRING)
    private DailyAttendanceStatus originalStatus;

    // Requested/calculated preview — computed at submission time
    private Integer requestedWorkingMinutes;
    private Integer requestedBreakMinutes;
    private Integer requestedOfficeMinutes;
    private Integer requestedOvertimeMinutes;

    @Enumerated(EnumType.STRING)
    private DailyAttendanceStatus requestedStatus;

    // Final approved values — populated only on APPROVED, from the actual
    // post-recalculation JobDailySummary (may differ from requested if data changed
    // between submission and approval)
    private Integer finalWorkingMinutes;
    private Integer finalBreakMinutes;
    private Integer finalOfficeMinutes;
    private Integer finalOvertimeMinutes;

    @Enumerated(EnumType.STRING)
    private DailyAttendanceStatus finalStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CorrectionRequestStatus status = CorrectionRequestStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private EmployeeDetails approvedBy;

    private String managerComment;

    @CreationTimestamp
    private LocalDateTime submittedAt;

    private LocalDateTime actionDate;
}
