package com.emp.management.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkReportEmailSettingsDTO {
    private boolean enabled;
    private int sendHour;
    private int sendMinute;
    private boolean notifyEmployees;
    private boolean notifyAdmin;
}
