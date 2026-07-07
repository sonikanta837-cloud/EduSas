package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "video_interviews", indexes = {
    @Index(name = "idx_vi_token",  columnList = "token",  unique = true),
    @Index(name = "idx_vi_status", columnList = "status"),
    @Index(name = "idx_vi_email",  columnList = "email")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoInterview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String candidateName;

    @Column(nullable = false, length = 200)
    private String email;

    @Column(length = 20)
    private String mobile;

    @Column(length = 150)
    private String position;

    @Column(length = 100)
    private String technology;

    @Column(length = 50)
    private String experience;

    @Column(nullable = false)
    @Builder.Default
    private Integer durationMinutes = 45;

    @Column(nullable = false)
    @Builder.Default
    private Integer numQuestions = 25;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private InterviewDifficulty difficulty = InterviewDifficulty.MIXED;

    @Column(unique = true, nullable = false, length = 50)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private VideoInterviewStatus status = VideoInterviewStatus.PENDING;

    @Column(length = 100)
    private String createdBy;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;
    private LocalDateTime expiresAt;

    @Column(length = 500)
    private String videoUrl;

    private Integer score;
    private Integer totalMarks;

    @Column(nullable = false)
    @Builder.Default
    private Integer violationCount = 0;

    @Column(length = 50)
    private String ipAddress;

    @OneToMany(mappedBy = "interview", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<VideoInterviewQuestion> questions = new ArrayList<>();

    @OneToMany(mappedBy = "interview", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<VideoInterviewViolation> violations = new ArrayList<>();
}
