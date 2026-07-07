package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ats_tech_interview_questions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AtsTechInterviewQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private AtsTechnicalInterview interview;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private InterviewQuestion question;

    @Column(name = "question_order")
    private Integer questionOrder;

    // Comma-separated original option letters in shuffled display order, e.g. "C,A,D,B"
    // Null for TEXT-type questions.
    @Column(name = "option_order", length = 20)
    private String optionOrder;
}
