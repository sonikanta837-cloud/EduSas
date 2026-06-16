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
public class PipAuditLogDTO {
    private Long id;
    private Long pipId;
    private Long performedById;
    private String performedByName;
    private String action;
    private String details;
    private LocalDateTime createdAt;
}
