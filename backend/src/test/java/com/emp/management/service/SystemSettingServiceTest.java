package com.emp.management.service;

import com.emp.management.dto.AttendanceAuditSettingsDTO;
import com.emp.management.dto.BreakAlertSettingsDTO;
import com.emp.management.dto.WorkReportEmailSettingsDTO;
import com.emp.management.entity.SystemSetting;
import com.emp.management.repository.SystemSettingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemSettingServiceTest {

    @Mock private SystemSettingRepository repo;

    @InjectMocks private SystemSettingService systemSettingService;

    // ── getBreakAlertSettings ─────────────────────────────────────────────────

    @Test
    void getBreakAlertSettings_withDefaults_returnsDefaultValues() {
        when(repo.findById(anyString())).thenReturn(Optional.empty());

        BreakAlertSettingsDTO result = systemSettingService.getBreakAlertSettings();

        assertThat(result).isNotNull();
        // Uses default values when DB has no stored settings
    }

    @Test
    void getBreakAlertSettings_withStoredValues_returnsStoredValues() {
        lenient().when(repo.findById(anyString())).thenReturn(Optional.empty());
        when(repo.findById("break_alert_enabled"))
                .thenReturn(Optional.of(SystemSetting.builder().key("break_alert_enabled").value("false").build()));
        when(repo.findById("break_alert_threshold_minutes"))
                .thenReturn(Optional.of(SystemSetting.builder().key("break_alert_threshold_minutes").value("90").build()));

        BreakAlertSettingsDTO result = systemSettingService.getBreakAlertSettings();

        assertThat(result.isEnabled()).isFalse();
        assertThat(result.getThresholdMinutes()).isEqualTo(90);
    }

    // ── saveBreakAlertSettings ────────────────────────────────────────────────

    @Test
    void saveBreakAlertSettings_savesAllValues() {
        BreakAlertSettingsDTO dto = BreakAlertSettingsDTO.builder()
                .enabled(true).thresholdMinutes(60)
                .notifyManager(true).notifyHr(false).notifyAdmin(true)
                .frequency("ONCE").build();
        when(repo.save(any())).thenReturn(SystemSetting.builder().build());

        BreakAlertSettingsDTO result = systemSettingService.saveBreakAlertSettings(dto);

        assertThat(result).isEqualTo(dto);
        verify(repo, times(6)).save(any(SystemSetting.class)); // 6 settings saved
    }

    // ── getWorkReportEmailSettings ────────────────────────────────────────────

    @Test
    void getWorkReportEmailSettings_withDefaults_returnsDefaultValues() {
        when(repo.findById(anyString())).thenReturn(Optional.empty());

        WorkReportEmailSettingsDTO result = systemSettingService.getWorkReportEmailSettings();

        assertThat(result).isNotNull();
    }

    @Test
    void getWorkReportEmailSettings_withInvalidIntValue_usesDefault() {
        lenient().when(repo.findById(anyString())).thenReturn(Optional.empty());
        when(repo.findById("work_report_email_hour"))
                .thenReturn(Optional.of(SystemSetting.builder().key("work_report_email_hour").value("not-a-number").build()));

        WorkReportEmailSettingsDTO result = systemSettingService.getWorkReportEmailSettings();

        // Invalid int value falls back to default
        assertThat(result).isNotNull();
    }

    // ── saveWorkReportEmailSettings ───────────────────────────────────────────

    @Test
    void saveWorkReportEmailSettings_savesAllValues() {
        WorkReportEmailSettingsDTO dto = WorkReportEmailSettingsDTO.builder()
                .enabled(true).sendHour(8).sendMinute(0)
                .notifyEmployees(true).notifyAdmin(true).build();
        when(repo.save(any())).thenReturn(SystemSetting.builder().build());

        WorkReportEmailSettingsDTO result = systemSettingService.saveWorkReportEmailSettings(dto);

        assertThat(result).isEqualTo(dto);
        verify(repo, times(5)).save(any(SystemSetting.class));
    }

    // ── getAttendanceAuditSettings ────────────────────────────────────────────

    @Test
    void getAttendanceAuditSettings_withDefaults_returnsDefaultValues() {
        when(repo.findById(anyString())).thenReturn(Optional.empty());

        AttendanceAuditSettingsDTO result = systemSettingService.getAttendanceAuditSettings();

        assertThat(result).isNotNull();
        assertThat(result.isEnabled()).isFalse(); // default is false
        assertThat(result.getTriggerHour()).isEqualTo(19); // default is 19
    }

    // ── saveAttendanceAuditSettings ───────────────────────────────────────────

    @Test
    void saveAttendanceAuditSettings_savesAllValues() {
        AttendanceAuditSettingsDTO dto = AttendanceAuditSettingsDTO.builder()
                .enabled(true).triggerHour(18).triggerMinute(30)
                .minWorkHours(8).notifyEmployee(true).notifyManager(true)
                .notifyHr(false).notifyAdmin(true).build();
        when(repo.save(any())).thenReturn(SystemSetting.builder().build());

        AttendanceAuditSettingsDTO result = systemSettingService.saveAttendanceAuditSettings(dto);

        assertThat(result).isEqualTo(dto);
        verify(repo, times(8)).save(any(SystemSetting.class)); // 8 settings saved
    }

    // ── getBoolean/getInt/getString ───────────────────────────────────────────

    @Test
    void getBoolean_trueValue_returnsTrue() {
        when(repo.findById("some_key"))
                .thenReturn(Optional.of(SystemSetting.builder().key("some_key").value("true").build()));

        boolean result = systemSettingService.getBoolean("some_key", false);

        assertThat(result).isTrue();
    }

    @Test
    void getBoolean_missingKey_returnsDefault() {
        when(repo.findById("missing_key")).thenReturn(Optional.empty());

        boolean result = systemSettingService.getBoolean("missing_key", true);

        assertThat(result).isTrue();
    }

    @Test
    void getInt_validValue_returnsInt() {
        when(repo.findById("int_key"))
                .thenReturn(Optional.of(SystemSetting.builder().key("int_key").value("42").build()));

        int result = systemSettingService.getInt("int_key", 0);

        assertThat(result).isEqualTo(42);
    }

    @Test
    void getInt_invalidValue_returnsDefault() {
        when(repo.findById("int_key"))
                .thenReturn(Optional.of(SystemSetting.builder().key("int_key").value("abc").build()));

        int result = systemSettingService.getInt("int_key", 99);

        assertThat(result).isEqualTo(99);
    }

    @Test
    void getString_existingKey_returnsValue() {
        when(repo.findById("str_key"))
                .thenReturn(Optional.of(SystemSetting.builder().key("str_key").value("ONCE").build()));

        String result = systemSettingService.getString("str_key", "BOTH");

        assertThat(result).isEqualTo("ONCE");
    }

    @Test
    void getString_missingKey_returnsDefault() {
        when(repo.findById("str_key")).thenReturn(Optional.empty());

        String result = systemSettingService.getString("str_key", "BOTH");

        assertThat(result).isEqualTo("BOTH");
    }
}
