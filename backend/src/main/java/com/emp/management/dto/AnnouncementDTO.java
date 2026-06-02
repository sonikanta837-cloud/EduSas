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
    private String location;
    private String category;
    private String priority;
    private boolean archived;
    private boolean readByCurrentUser;
    private int viewedByCount;
    private int totalActiveEmployees;
    private LocalDateTime createdAt;
}
