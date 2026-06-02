package com.emp.management.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseNotificationDTO {
    private Long id;
    private Long courseId;
    private String courseTitle;
    private String courseDescription;
    private boolean read;
    private LocalDateTime createdAt;
}
