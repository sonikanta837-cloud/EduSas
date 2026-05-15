package com.emp.management.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnnouncementDTO {
    private Long id;
    private String title;
    private String body;
    private String authorName;
    private boolean pinned;
    private LocalDateTime createdAt;
}
