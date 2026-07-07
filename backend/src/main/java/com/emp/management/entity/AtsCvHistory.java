package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ats_cv_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AtsCvHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private AtsCandidate candidate;

    @Column(name = "previous_resume_path")
    private String previousResumePath;

    @Column(name = "previous_resume_name")
    private String previousResumeName;

    @Column(name = "previous_upload_date")
    private LocalDateTime previousUploadDate;

    @Column(name = "new_resume_path")
    private String newResumePath;

    @Column(name = "new_resume_name")
    private String newResumeName;

    @Column(name = "replaced_by", length = 200)
    private String replacedBy;

    @CreationTimestamp
    @Column(name = "replaced_at", updatable = false)
    private LocalDateTime replacedAt;
}
