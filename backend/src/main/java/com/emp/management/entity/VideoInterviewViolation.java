package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "video_interview_violations", indexes = {
    @Index(name = "idx_viv_interview", columnList = "interview_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoInterviewViolation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private VideoInterview interview;

    @Column(length = 100)
    private String violationType;

    @Column(length = 100)
    private String browserEvent;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 50)
    private String ipAddress;

    private Integer violationNumber;

    private LocalDateTime occurredAt;
}
