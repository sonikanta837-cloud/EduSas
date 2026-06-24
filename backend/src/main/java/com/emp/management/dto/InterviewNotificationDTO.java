package com.emp.management.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewNotificationDTO {
    private Long id;
    private Long roundId;
    private String title;
    private String message;
    private boolean read;
    private LocalDateTime createdAt;
    private LocalDateTime scheduledAt;
    private String candidateName;
    private String candidatePosition;
    private String roundType;
    private String location;
}
