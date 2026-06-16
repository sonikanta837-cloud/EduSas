package com.emp.management.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BreakAlertSettingsDTO {
    private boolean enabled;
    private int thresholdMinutes;
    private boolean notifyManager;
    private boolean notifyHr;
    private boolean notifyAdmin;
    private String frequency; // ON_LOGOUT | SCHEDULED | BOTH
}
