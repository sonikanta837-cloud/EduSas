package com.emp.management.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerformanceThresholdSettingsDTO {
    private double lowRatingThreshold;
}
