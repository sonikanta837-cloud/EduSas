package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "exams")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Exam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Course course;

    @Column(columnDefinition = "JSON")
    private String questions;

    @Column(nullable = false)
    @Builder.Default
    private Integer passingScore = 70;

    @Column(nullable = false)
    @Builder.Default
    private Integer totalMarks = 100;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
