package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.dto.AttendanceAuditSettingsDTO;
import com.emp.management.dto.BreakAlertSettingsDTO;
import com.emp.management.dto.WorkReportEmailSettingsDTO;
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

    // ── GET /api/settings/break-alert ─────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getBreakAlertSettings_asAdmin_returns200() throws Exception {
        BreakAlertSettingsDTO dto = new BreakAlertSettingsDTO();
        when(systemSettingService.getBreakAlertSettings()).thenReturn(dto);

        mockMvc.perform(get("/api/settings/break-alert"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getBreakAlertSettings_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/settings/break-alert"))
                .andExpect(status().isForbidden());
    }

    // ── PUT /api/settings/break-alert ─────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void saveBreakAlertSettings_asAdmin_returns200() throws Exception {
        BreakAlertSettingsDTO dto = new BreakAlertSettingsDTO();
        when(systemSettingService.saveBreakAlertSettings(any())).thenReturn(dto);

        mockMvc.perform(put("/api/settings/break-alert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    // ── GET /api/settings/work-report-email ───────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getWorkReportEmailSettings_asAdmin_returns200() throws Exception {
        WorkReportEmailSettingsDTO dto = new WorkReportEmailSettingsDTO();
        when(systemSettingService.getWorkReportEmailSettings()).thenReturn(dto);

        mockMvc.perform(get("/api/settings/work-report-email"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getWorkReportEmailSettings_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/settings/work-report-email"))
                .andExpect(status().isForbidden());
    }

    // ── PUT /api/settings/work-report-email ───────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void saveWorkReportEmailSettings_asAdmin_returns200() throws Exception {
        WorkReportEmailSettingsDTO dto = new WorkReportEmailSettingsDTO();
        when(systemSettingService.saveWorkReportEmailSettings(any())).thenReturn(dto);

        mockMvc.perform(put("/api/settings/work-report-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    // ── GET /api/settings/attendance-audit ────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAttendanceAuditSettings_asAdmin_returns200() throws Exception {
        AttendanceAuditSettingsDTO dto = new AttendanceAuditSettingsDTO();
        when(systemSettingService.getAttendanceAuditSettings()).thenReturn(dto);

        mockMvc.perform(get("/api/settings/attendance-audit"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getAttendanceAuditSettings_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/settings/attendance-audit"))
                .andExpect(status().isForbidden());
    }

    // ── PUT /api/settings/attendance-audit ────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void saveAttendanceAuditSettings_asAdmin_returns200() throws Exception {
        AttendanceAuditSettingsDTO dto = new AttendanceAuditSettingsDTO();
        when(systemSettingService.saveAttendanceAuditSettings(any())).thenReturn(dto);

        mockMvc.perform(put("/api/settings/attendance-audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }
}
