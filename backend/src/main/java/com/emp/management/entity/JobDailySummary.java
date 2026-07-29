package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_daily_summaries",
        uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "work_date"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobDailySummary {

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

    @Builder.Default
    private Integer totalWorkingMinutes = 0;

    @Builder.Default
    private Integer totalBreakMinutes = 0;

    @Builder.Default
    private Integer sessionCount = 0;

    // First login / last logout across all of the day's job sessions
    private LocalDateTime firstLoginTime;
    private LocalDateTime lastLogoutTime;

    // Client worked for the day, if every session shares one; "Multiple" if not; null if no sessions
    private String primaryClient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DailyAttendanceStatus status;

    @Column(nullable = false)
    @Builder.Default
    private boolean underHoursAlertSent = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DayCorrectionStatus correctionStatus = DayCorrectionStatus.NONE;

    // Convenience pointer to the current/latest correction request for this day
    private Long latestCorrectionRequestId;

    private LocalDateTime computedAt;
}
