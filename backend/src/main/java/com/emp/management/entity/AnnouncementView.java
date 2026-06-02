package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "announcement_views",
       uniqueConstraints = @UniqueConstraint(columnNames = {"announcement_id", "employee_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnnouncementView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "announcement_id", nullable = false)
    private Announcement announcement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private EmployeeDetails employee;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime viewedAt;
}
