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
public class CertificateDTO {
    private Long id;
    private String courseTitle;
    private String certificateNumber;
    private LocalDateTime issuedAt;
    private String employeeName;
    private Long employeeId;
    private Integer score;
}
