package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.dto.BreakAlertSettingsDTO;
import com.emp.management.dto.PerformanceThresholdSettingsDTO;
import com.emp.management.exception.GlobalExceptionHandler;
import com.emp.management.security.JwtAuthFilter;
import com.emp.management.service.SystemSettingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = SystemSettingController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class SystemSettingControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private SystemSettingService systemSettingService;

    // ── read is also ADMIN/DIRECTOR only (unlike most other read endpoints) ──

    @Test
    @WithMockUser(roles = "ADMIN")
    void getBreakAlertSettings_asAdmin_returns200() throws Exception {
        when(systemSettingService.getBreakAlertSettings())
                .thenReturn(BreakAlertSettingsDTO.builder().enabled(true).thresholdMinutes(75).build());

        mockMvc.perform(get("/api/settings/break-alert"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.thresholdMinutes").value(75));
    }

    @Test
    @WithMockUser(roles = "HR")
    void getBreakAlertSettings_asHR_returns403() throws Exception {
        mockMvc.perform(get("/api/settings/break-alert"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void getBreakAlertSettings_asManager_returns403() throws Exception {
        mockMvc.perform(get("/api/settings/break-alert"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getBreakAlertSettings_unauthenticated_returns401or403() throws Exception {
        mockMvc.perform(get("/api/settings/break-alert"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assert status == 401 || status == 403;
                });
    }

    @Test
    @WithMockUser(roles = "DIRECTOR")
    void saveBreakAlertSettings_asDirector_returns200() throws Exception {
        BreakAlertSettingsDTO dto = BreakAlertSettingsDTO.builder().enabled(true).thresholdMinutes(60).frequency("BOTH").build();
        when(systemSettingService.saveBreakAlertSettings(any())).thenReturn(dto);

        mockMvc.perform(put("/api/settings/break-alert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.thresholdMinutes").value(60));
    }

    @Test
    @WithMockUser(roles = "HR")
    void saveBreakAlertSettings_asHR_returns403() throws Exception {
        BreakAlertSettingsDTO dto = BreakAlertSettingsDTO.builder().enabled(true).build();

        mockMvc.perform(put("/api/settings/break-alert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getPerformanceThresholdSettings_asAdmin_returns200() throws Exception {
        when(systemSettingService.getPerformanceThresholdSettings())
                .thenReturn(PerformanceThresholdSettingsDTO.builder().lowRatingThreshold(3.0).build());

        mockMvc.perform(get("/api/settings/performance-threshold"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lowRatingThreshold").value(3.0));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void savePerformanceThresholdSettings_asAdmin_returns200() throws Exception {
        PerformanceThresholdSettingsDTO dto = PerformanceThresholdSettingsDTO.builder().lowRatingThreshold(3.5).build();
        when(systemSettingService.savePerformanceThresholdSettings(any())).thenReturn(dto);

        mockMvc.perform(put("/api/settings/performance-threshold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lowRatingThreshold").value(3.5));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getCorrectionPolicySettings_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/settings/correction-policy"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAttendanceAuditSettings_asAdmin_returns200() throws Exception {
        when(systemSettingService.getAttendanceAuditSettings())
                .thenReturn(com.emp.management.dto.AttendanceAuditSettingsDTO.builder().enabled(true).build());

        mockMvc.perform(get("/api/settings/attendance-audit"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getWorkReportEmailSettings_asAdmin_returns200() throws Exception {
        when(systemSettingService.getWorkReportEmailSettings())
                .thenReturn(com.emp.management.dto.WorkReportEmailSettingsDTO.builder().enabled(true).build());

        mockMvc.perform(get("/api/settings/work-report-email"))
                .andExpect(status().isOk());
    }
}
