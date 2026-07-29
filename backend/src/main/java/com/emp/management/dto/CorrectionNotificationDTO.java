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
public class CorrectionNotificationDTO {
    private Long id;
    private Long correctionRequestId;
    private String senderName;
    private String message;
    private boolean read;
    private LocalDateTime createdAt;
}
