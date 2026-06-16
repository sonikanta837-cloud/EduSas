package com.emp.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceAuditSettingsDTO {
    private boolean enabled;
    private int triggerHour;
    private int triggerMinute;
    private int minWorkHours;
    private boolean notifyEmployee;
    private boolean notifyManager;
    private boolean notifyHr;
    private boolean notifyAdmin;
}
