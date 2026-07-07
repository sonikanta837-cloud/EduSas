package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "video_interview_answers", indexes = {
    @Index(name = "idx_via_interview", columnList = "interview_id"),
    @Index(name = "idx_via_question",  columnList = "interview_id, question_id", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoInterviewAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private VideoInterview interview;

    @Column(nullable = false)
    private Long questionId;

    @Column(columnDefinition = "TEXT")
    private String answerText;

    @Column(length = 1)
    private String selectedOption;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isMarked = false;

    private LocalDateTime savedAt;
}
