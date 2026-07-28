package com.emp.management.controller;

import com.emp.management.dto.AttendanceAuditSettingsDTO;
import com.emp.management.dto.BreakAlertSettingsDTO;
import com.emp.management.dto.PerformanceThresholdSettingsDTO;
import com.emp.management.dto.WorkReportEmailSettingsDTO;
import com.emp.management.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SystemSettingController {

    private final SystemSettingService systemSettingService;

    @GetMapping("/break-alert")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public ResponseEntity<BreakAlertSettingsDTO> getBreakAlertSettings() {
        return ResponseEntity.ok(systemSettingService.getBreakAlertSettings());
    }

    @PutMapping("/break-alert")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public ResponseEntity<BreakAlertSettingsDTO> saveBreakAlertSettings(
            @RequestBody BreakAlertSettingsDTO dto) {
        return ResponseEntity.ok(systemSettingService.saveBreakAlertSettings(dto));
    }

    @GetMapping("/work-report-email")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public ResponseEntity<WorkReportEmailSettingsDTO> getWorkReportEmailSettings() {
        return ResponseEntity.ok(systemSettingService.getWorkReportEmailSettings());
    }

    @PutMapping("/work-report-email")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public ResponseEntity<WorkReportEmailSettingsDTO> saveWorkReportEmailSettings(
            @RequestBody WorkReportEmailSettingsDTO dto) {
        return ResponseEntity.ok(systemSettingService.saveWorkReportEmailSettings(dto));
    }

    @GetMapping("/attendance-audit")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public ResponseEntity<AttendanceAuditSettingsDTO> getAttendanceAuditSettings() {
        return ResponseEntity.ok(systemSettingService.getAttendanceAuditSettings());
    }

    @PutMapping("/attendance-audit")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public ResponseEntity<AttendanceAuditSettingsDTO> saveAttendanceAuditSettings(
            @RequestBody AttendanceAuditSettingsDTO dto) {
        return ResponseEntity.ok(systemSettingService.saveAttendanceAuditSettings(dto));
    }

    @GetMapping("/performance-threshold")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public ResponseEntity<PerformanceThresholdSettingsDTO> getPerformanceThresholdSettings() {
        return ResponseEntity.ok(systemSettingService.getPerformanceThresholdSettings());
    }

    @PutMapping("/performance-threshold")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public ResponseEntity<PerformanceThresholdSettingsDTO> savePerformanceThresholdSettings(
            @RequestBody PerformanceThresholdSettingsDTO dto) {
        return ResponseEntity.ok(systemSettingService.savePerformanceThresholdSettings(dto));
    }
}
