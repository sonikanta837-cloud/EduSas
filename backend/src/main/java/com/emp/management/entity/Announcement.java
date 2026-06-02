package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "announcements")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false)
    private String authorName;

    @Column(nullable = false)
    private boolean pinned;

    private String location;

    private String category;

    private String priority;

    @Column(nullable = false)
    private int viewCount;

    @Column(nullable = false)
    private boolean archived;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
