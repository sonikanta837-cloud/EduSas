package com.emp.management.service;

import com.emp.management.dto.AttendanceAuditSettingsDTO;
import com.emp.management.dto.BreakAlertSettingsDTO;
import com.emp.management.dto.PerformanceThresholdSettingsDTO;
import com.emp.management.dto.WorkReportEmailSettingsDTO;
import com.emp.management.entity.SystemSetting;
import com.emp.management.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SystemSettingService {

    private static final String KEY_BREAK_ENABLED    = "break_alert_enabled";
    private static final String KEY_BREAK_THRESHOLD  = "break_alert_threshold_minutes";
    private static final String KEY_BREAK_MANAGER    = "break_alert_notify_manager";
    private static final String KEY_BREAK_HR         = "break_alert_notify_hr";
    private static final String KEY_BREAK_ADMIN      = "break_alert_notify_admin";
    private static final String KEY_BREAK_FREQUENCY  = "break_alert_frequency";

    private final SystemSettingRepository repo;

    private static final String KEY_WR_ENABLED          = "work_report_email_enabled";
    private static final String KEY_WR_HOUR             = "work_report_email_hour";
    private static final String KEY_WR_MINUTE           = "work_report_email_minute";
    private static final String KEY_WR_NOTIFY_EMPLOYEES = "work_report_notify_employees";
    private static final String KEY_WR_NOTIFY_ADMIN     = "work_report_notify_admin";

    private static final String KEY_AUDIT_ENABLED       = "attendance_audit_enabled";
    private static final String KEY_AUDIT_HOUR          = "attendance_audit_trigger_hour";
    private static final String KEY_AUDIT_MINUTE        = "attendance_audit_trigger_minute";
    private static final String KEY_AUDIT_MIN_HOURS     = "attendance_audit_min_work_hours";
    private static final String KEY_AUDIT_EMPLOYEE      = "attendance_audit_notify_employee";
    private static final String KEY_AUDIT_MANAGER       = "attendance_audit_notify_manager";
    private static final String KEY_AUDIT_HR            = "attendance_audit_notify_hr";
    private static final String KEY_AUDIT_ADMIN         = "attendance_audit_notify_admin";

    private static final String KEY_PERF_LOW_RATING_THRESHOLD = "performance_low_rating_threshold";

    @Value("${app.performance.low-rating-threshold:3.0}")
    private double defaultLowRatingThreshold;

    @Value("${app.attendance.break-alert.enabled:true}")
    private boolean defaultEnabled;

    @Value("${app.attendance.break-alert.threshold-minutes:75}")
    private int defaultThreshold;

    @Value("${app.work-report.email.hour:10}")
    private int defaultWrHour;

    @Value("${app.work-report.email.minute:0}")
    private int defaultWrMinute;

    private String get(String key, String fallback) {
        return repo.findById(key).map(SystemSetting::getValue).orElse(fallback);
    }

    public boolean getBoolean(String key, boolean fallback) {
        return Boolean.parseBoolean(get(key, String.valueOf(fallback)));
    }

    public int getInt(String key, int fallback) {
        try { return Integer.parseInt(get(key, String.valueOf(fallback))); }
        catch (NumberFormatException e) { return fallback; }
    }

    public String getString(String key, String fallback) {
        return get(key, fallback);
    }

    @Transactional(readOnly = true)
    public BreakAlertSettingsDTO getBreakAlertSettings() {
        return BreakAlertSettingsDTO.builder()
                .enabled(getBoolean(KEY_BREAK_ENABLED, defaultEnabled))
                .thresholdMinutes(getInt(KEY_BREAK_THRESHOLD, defaultThreshold))
                .notifyManager(getBoolean(KEY_BREAK_MANAGER, true))
                .notifyHr(getBoolean(KEY_BREAK_HR, true))
                .notifyAdmin(getBoolean(KEY_BREAK_ADMIN, false))
                .frequency(getString(KEY_BREAK_FREQUENCY, "BOTH"))
                .build();
    }

    @Transactional
    public BreakAlertSettingsDTO saveBreakAlertSettings(BreakAlertSettingsDTO dto) {
        save(KEY_BREAK_ENABLED,   String.valueOf(dto.isEnabled()));
        save(KEY_BREAK_THRESHOLD, String.valueOf(dto.getThresholdMinutes()));
        save(KEY_BREAK_MANAGER,   String.valueOf(dto.isNotifyManager()));
        save(KEY_BREAK_HR,        String.valueOf(dto.isNotifyHr()));
        save(KEY_BREAK_ADMIN,     String.valueOf(dto.isNotifyAdmin()));
        save(KEY_BREAK_FREQUENCY, dto.getFrequency());
        return dto;
    }

    @Transactional(readOnly = true)
    public WorkReportEmailSettingsDTO getWorkReportEmailSettings() {
        return WorkReportEmailSettingsDTO.builder()
                .enabled(getBoolean(KEY_WR_ENABLED, true))
                .sendHour(getInt(KEY_WR_HOUR, defaultWrHour))
                .sendMinute(getInt(KEY_WR_MINUTE, defaultWrMinute))
                .notifyEmployees(getBoolean(KEY_WR_NOTIFY_EMPLOYEES, true))
                .notifyAdmin(getBoolean(KEY_WR_NOTIFY_ADMIN, true))
                .build();
    }

    @Transactional
    public WorkReportEmailSettingsDTO saveWorkReportEmailSettings(WorkReportEmailSettingsDTO dto) {
        save(KEY_WR_ENABLED,          String.valueOf(dto.isEnabled()));
        save(KEY_WR_HOUR,             String.valueOf(dto.getSendHour()));
        save(KEY_WR_MINUTE,           String.valueOf(dto.getSendMinute()));
        save(KEY_WR_NOTIFY_EMPLOYEES, String.valueOf(dto.isNotifyEmployees()));
        save(KEY_WR_NOTIFY_ADMIN,     String.valueOf(dto.isNotifyAdmin()));
        return dto;
    }

    @Transactional(readOnly = true)
    public AttendanceAuditSettingsDTO getAttendanceAuditSettings() {
        return AttendanceAuditSettingsDTO.builder()
                .enabled(getBoolean(KEY_AUDIT_ENABLED, false))
                .triggerHour(getInt(KEY_AUDIT_HOUR, 19))
                .triggerMinute(getInt(KEY_AUDIT_MINUTE, 0))
                .minWorkHours(getInt(KEY_AUDIT_MIN_HOURS, 8))
                .notifyEmployee(getBoolean(KEY_AUDIT_EMPLOYEE, false))
                .notifyManager(getBoolean(KEY_AUDIT_MANAGER, true))
                .notifyHr(getBoolean(KEY_AUDIT_HR, false))
                .notifyAdmin(getBoolean(KEY_AUDIT_ADMIN, true))
                .build();
    }

    @Transactional
    public AttendanceAuditSettingsDTO saveAttendanceAuditSettings(AttendanceAuditSettingsDTO dto) {
        save(KEY_AUDIT_ENABLED,   String.valueOf(dto.isEnabled()));
        save(KEY_AUDIT_HOUR,      String.valueOf(dto.getTriggerHour()));
        save(KEY_AUDIT_MINUTE,    String.valueOf(dto.getTriggerMinute()));
        save(KEY_AUDIT_MIN_HOURS, String.valueOf(dto.getMinWorkHours()));
        save(KEY_AUDIT_EMPLOYEE,  String.valueOf(dto.isNotifyEmployee()));
        save(KEY_AUDIT_MANAGER,   String.valueOf(dto.isNotifyManager()));
        save(KEY_AUDIT_HR,        String.valueOf(dto.isNotifyHr()));
        save(KEY_AUDIT_ADMIN,     String.valueOf(dto.isNotifyAdmin()));
        return dto;
    }

    public double getLowRatingThreshold() {
        try { return Double.parseDouble(get(KEY_PERF_LOW_RATING_THRESHOLD, String.valueOf(defaultLowRatingThreshold))); }
        catch (NumberFormatException e) { return defaultLowRatingThreshold; }
    }

    @Transactional(readOnly = true)
    public PerformanceThresholdSettingsDTO getPerformanceThresholdSettings() {
        return PerformanceThresholdSettingsDTO.builder()
                .lowRatingThreshold(getLowRatingThreshold())
                .build();
    }

    @Transactional
    public PerformanceThresholdSettingsDTO savePerformanceThresholdSettings(PerformanceThresholdSettingsDTO dto) {
        save(KEY_PERF_LOW_RATING_THRESHOLD, String.valueOf(dto.getLowRatingThreshold()));
        return dto;
    }

    private void save(String key, String value) {
        repo.save(SystemSetting.builder()
                .key(key).value(value).updatedAt(LocalDateTime.now()).build());
    }
}
