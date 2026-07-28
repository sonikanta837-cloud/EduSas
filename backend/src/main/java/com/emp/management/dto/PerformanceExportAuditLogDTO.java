package com.emp.management.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerformanceExportAuditLogDTO {
    private Long id;
    private String performedByName;
    private String format;
    private String scope;
    private Integer recordCount;
    private String filterSummary;
    private LocalDateTime createdAt;
}
