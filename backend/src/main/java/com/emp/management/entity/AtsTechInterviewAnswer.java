package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ats_tech_interview_answers",
    uniqueConstraints = @UniqueConstraint(columnNames = {"interview_id", "question_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AtsTechInterviewAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private AtsTechnicalInterview interview;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    // For TEXT questions
    @Column(name = "answer_text", columnDefinition = "TEXT")
    private String answerText;

    // For MCQ questions — original option letter (A/B/C/D), not display letter
    @Column(name = "selected_option", length = 1)
    private String selectedOption;

    @UpdateTimestamp
    private LocalDateTime answeredAt;
}
