package com.emp.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseDTO {
    private Long id;
    private String title;
    private String description;
    private String content;
    private String youtubeUrl;
    private Integer durationHours;
    private boolean active;
    private String createdByName;
    private Long createdById;
    private LocalDateTime createdAt;
    private int enrollmentCount;
    private boolean hasExam;
    private Integer examPassingScore;
    private String enrollmentStatus;
    private Integer examScore;
    private String certificateNumber;
}
