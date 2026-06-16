package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "daily_work_reports",
       uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "report_date"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyWorkReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private EmployeeDetails employee;

    @Column(nullable = false)
    private LocalDate reportDate;

    private LocalTime firstLoginTime;
    private LocalTime lastLogoutTime;

    private Integer totalOfficeMinutes;  // lastLogout − firstLogin
    private Integer activeMinutes;        // sum of all session durations
    private Integer breakMinutes;         // totalOffice − active
    private Integer sessionCount;

    @CreationTimestamp
    private LocalDateTime generatedAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
