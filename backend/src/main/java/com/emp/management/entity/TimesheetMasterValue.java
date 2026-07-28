package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "timesheet_master_values",
        uniqueConstraints = @UniqueConstraint(columnNames = {"type", "value"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimesheetMasterValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MasterDataType type;

    @Column(nullable = false)
    private String value;

    // Only populated when type = PERIOD_END — used for chronological sorting
    private LocalDate periodEndDate;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    private String createdByName;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
