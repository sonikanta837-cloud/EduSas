package com.emp.management.service;

import com.emp.management.dto.*;
import com.emp.management.entity.SystemSetting;
import com.emp.management.repository.SystemSettingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemSettingServiceTest {

    @Mock private SystemSettingRepository repo;

    @InjectMocks private SystemSettingService systemSettingService;

    private SystemSetting setting(String key, String value) {
        return SystemSetting.builder().key(key).value(value).build();
    }

    // ── getBoolean / getInt fallback behavior ────────────────────────────────

    @Test
    void getBoolean_settingNotPresent_returnsFallback() {
        when(repo.findById("some_key")).thenReturn(Optional.empty());

        assertThat(systemSettingService.getBoolean("some_key", true)).isTrue();
        assertThat(systemSettingService.getBoolean("some_key", false)).isFalse();
    }

    @Test
    void getBoolean_settingPresent_parsesStoredValue() {
        when(repo.findById("some_key")).thenReturn(Optional.of(setting("some_key", "true")));

        assertThat(systemSettingService.getBoolean("some_key", false)).isTrue();
    }

    @Test
    void getInt_settingNotPresent_returnsFallback() {
        when(repo.findById("some_key")).thenReturn(Optional.empty());

        assertThat(systemSettingService.getInt("some_key", 42)).isEqualTo(42);
    }

    @Test
    void getInt_malformedStoredValue_returnsFallback() {
        when(repo.findById("some_key")).thenReturn(Optional.of(setting("some_key", "not-a-number")));

        assertThat(systemSettingService.getInt("some_key", 99)).isEqualTo(99);
    }

    @Test
    void getInt_validStoredValue_returnsParsedValue() {
        when(repo.findById("some_key")).thenReturn(Optional.of(setting("some_key", "123")));

        assertThat(systemSettingService.getInt("some_key", 0)).isEqualTo(123);
    }

    // ── Break Alert settings ──────────────────────────────────────────────────

    @Test
    void getBreakAlertSettings_noStoredValues_usesConfiguredDefaults() {
        ReflectionTestUtils.setField(systemSettingService, "defaultEnabled", true);
        ReflectionTestUtils.setField(systemSettingService, "defaultThreshold", 75);
        when(repo.findById(any())).thenReturn(Optional.empty());

        BreakAlertSettingsDTO dto = systemSettingService.getBreakAlertSettings();

        assertThat(dto.isEnabled()).isTrue();
        assertThat(dto.getThresholdMinutes()).isEqualTo(75);
        assertThat(dto.getFrequency()).isEqualTo("BOTH");
    }

    @Test
    void saveBreakAlertSettings_persistsAllFieldsAsSettings() {
        BreakAlertSettingsDTO dto = BreakAlertSettingsDTO.builder()
                .enabled(true).thresholdMinutes(60).notifyManager(true).notifyHr(false).notifyAdmin(true)
                .frequency("SCHEDULED").build();
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BreakAlertSettingsDTO result = systemSettingService.saveBreakAlertSettings(dto);

        assertThat(result).isEqualTo(dto);
        verify(repo, times(6)).save(any()); // 6 keys persisted
    }

    // ── Performance Threshold settings ────────────────────────────────────────

    @Test
    void getLowRatingThreshold_notConfigured_usesApplicationDefault() {
        ReflectionTestUtils.setField(systemSettingService, "defaultLowRatingThreshold", 3.0);
        when(repo.findById(any())).thenReturn(Optional.empty());

        assertThat(systemSettingService.getLowRatingThreshold()).isEqualTo(3.0);
    }

    @Test
    void getLowRatingThreshold_configuredValue_isUsed() {
        when(repo.findById("performance_low_rating_threshold"))
                .thenReturn(Optional.of(setting("performance_low_rating_threshold", "3.5")));

        assertThat(systemSettingService.getLowRatingThreshold()).isEqualTo(3.5);
    }

    @Test
    void getLowRatingThreshold_malformedValue_fallsBackToDefault() {
        ReflectionTestUtils.setField(systemSettingService, "defaultLowRatingThreshold", 3.0);
        when(repo.findById("performance_low_rating_threshold"))
                .thenReturn(Optional.of(setting("performance_low_rating_threshold", "not-a-double")));

        assertThat(systemSettingService.getLowRatingThreshold()).isEqualTo(3.0);
    }

    @Test
    void savePerformanceThresholdSettings_persistsValue() {
        PerformanceThresholdSettingsDTO dto = PerformanceThresholdSettingsDTO.builder().lowRatingThreshold(4.0).build();
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PerformanceThresholdSettingsDTO result = systemSettingService.savePerformanceThresholdSettings(dto);

        assertThat(result.getLowRatingThreshold()).isEqualTo(4.0);
        verify(repo).save(argThat(s -> "performance_low_rating_threshold".equals(s.getKey()) && "4.0".equals(s.getValue())));
    }

    // ── Correction Policy settings (shared with JobDailySummaryService threshold) ──

    @Test
    void getCorrectionPolicySettings_defaultOvertimeThresholdIs480Minutes() {
        when(repo.findById(any())).thenReturn(Optional.empty());

        CorrectionPolicySettingsDTO dto = systemSettingService.getCorrectionPolicySettings();

        assertThat(dto.getOvertimeThresholdMinutes()).isEqualTo(480);
        assertThat(dto.isReminderEnabled()).isFalse();
    }

    @Test
    void saveCorrectionPolicySettings_persistsOvertimeThreshold() {
        CorrectionPolicySettingsDTO dto = CorrectionPolicySettingsDTO.builder()
                .overtimeThresholdMinutes(510).reminderEnabled(true).reminderHour(9).reminderMinute(30).build();
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        systemSettingService.saveCorrectionPolicySettings(dto);

        verify(repo).save(argThat(s ->
                SystemSettingService.KEY_CORR_OVERTIME_THRESHOLD_MINUTES.equals(s.getKey()) && "510".equals(s.getValue())));
    }

    // ── Attendance Audit settings ──────────────────────────────────────────────

    @Test
    void getAttendanceAuditSettings_noStoredValues_usesHardcodedDefaults() {
        when(repo.findById(any())).thenReturn(Optional.empty());

        AttendanceAuditSettingsDTO dto = systemSettingService.getAttendanceAuditSettings();

        assertThat(dto.isEnabled()).isFalse();
        assertThat(dto.getTriggerHour()).isEqualTo(19);
        assertThat(dto.getMinWorkHours()).isEqualTo(8);
    }

    @Test
    void saveAttendanceAuditSettings_persistsAllEightKeys() {
        AttendanceAuditSettingsDTO dto = AttendanceAuditSettingsDTO.builder()
                .enabled(true).triggerHour(11).triggerMinute(0).minWorkHours(8)
                .notifyEmployee(true).notifyManager(true).notifyHr(false).notifyAdmin(true).build();
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        systemSettingService.saveAttendanceAuditSettings(dto);

        verify(repo, times(8)).save(any());
    }

    // ── Work Report Email settings ─────────────────────────────────────────────

    @Test
    void getWorkReportEmailSettings_noStoredValues_usesConfiguredDefaults() {
        ReflectionTestUtils.setField(systemSettingService, "defaultWrHour", 10);
        ReflectionTestUtils.setField(systemSettingService, "defaultWrMinute", 0);
        when(repo.findById(any())).thenReturn(Optional.empty());

        WorkReportEmailSettingsDTO dto = systemSettingService.getWorkReportEmailSettings();

        assertThat(dto.isEnabled()).isTrue();
        assertThat(dto.getSendHour()).isEqualTo(10);
    }

    @Test
    void saveWorkReportEmailSettings_persistsAllFiveKeys() {
        WorkReportEmailSettingsDTO dto = WorkReportEmailSettingsDTO.builder()
                .enabled(false).sendHour(9).sendMinute(30).notifyEmployees(false).notifyAdmin(true).build();
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        systemSettingService.saveWorkReportEmailSettings(dto);

        verify(repo, times(5)).save(any());
    }
}
