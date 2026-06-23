package com.emp.management.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewAuditLogDTO {
    private Long id;
    private String entityType;
    private Long entityId;
    private String action;
    private Long performedById;
    private String performedByName;
    private String details;
    private LocalDateTime createdAt;
}
