package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "interview_questions", indexes = {
    @Index(name = "idx_iq_technology", columnList = "technology"),
    @Index(name = "idx_iq_difficulty", columnList = "difficulty"),
    @Index(name = "idx_iq_status",     columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(length = 100)
    private String category;

    @Column(length = 100)
    private String technology;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private QuestionDifficulty difficulty = QuestionDifficulty.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    @Builder.Default
    private QuestionType questionType = QuestionType.TEXT;

    @Column(columnDefinition = "TEXT")
    private String optionA;

    @Column(columnDefinition = "TEXT")
    private String optionB;

    @Column(columnDefinition = "TEXT")
    private String optionC;

    @Column(columnDefinition = "TEXT")
    private String optionD;

    @Column(length = 1)
    private String correctAnswer;

    @Column(columnDefinition = "TEXT")
    private String expectedKeywords;

    @Column(nullable = false)
    @Builder.Default
    private Integer marks = 5;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private QuestionStatus status = QuestionStatus.ACTIVE;

    @Column(length = 100)
    private String createdBy;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
