package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "resources")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Resource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String fileName;

    private String originalFileName;
    private String fileType;
    private Long fileSize;

    @Column(nullable = false)
    private String filePath;

    @Column(columnDefinition = "TEXT")
    private String description;

    // "POLICY" | "FORM_TEMPLATE" | "GENERAL"
    private String category;

    // sub-category within a section, e.g. "HR Policy", "IT Policy"
    private String subCategory;

    @Builder.Default
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User uploadedBy;

    @CreationTimestamp
    private LocalDateTime uploadedAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
