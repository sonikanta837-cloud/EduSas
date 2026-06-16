package com.emp.management.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipNotificationDTO {
    private Long id;
    private Long pipId;
    private String pipTitle;
    private String senderName;
    private String message;
    private boolean read;
    private LocalDateTime createdAt;
}
