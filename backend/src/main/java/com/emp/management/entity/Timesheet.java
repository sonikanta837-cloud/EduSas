package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "timesheets", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"employee_id", "work_date"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Timesheet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private EmployeeDetails employee;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    private LocalTime loginTime;
    private LocalTime logoutTime;

    private Double workingHours;

    private boolean manualOverride;   // true = hours were manually set; skip auto-recalc on logout

    @Column(columnDefinition = "TEXT")
    private String notes;

    private boolean alertSent;
    private boolean missingAlertSent;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
