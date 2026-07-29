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
public class CorrectionAuditLogDTO {
    private Long id;
    private String action;
    private String details;
    private String performedByName;
    private LocalDateTime createdAt;
}
