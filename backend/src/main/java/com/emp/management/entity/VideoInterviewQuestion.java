package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "video_interview_questions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoInterviewQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private VideoInterview interview;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "question_id", nullable = false)
    private InterviewQuestion question;

    @Column(nullable = false)
    private Integer questionOrder;

    /**
     * Shuffled MCQ option order stored as comma-separated original letters.
     * Example: "C,A,D,B" means display option C first, then A, then D, then B.
     * Null for TEXT questions.
     */
    @Column(length = 10)
    private String optionOrder;
}
