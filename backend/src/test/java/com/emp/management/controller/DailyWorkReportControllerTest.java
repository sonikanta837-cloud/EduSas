package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.dto.DailyWorkReportDTO;
import com.emp.management.exception.GlobalExceptionHandler;
import com.emp.management.security.JwtAuthFilter;
import com.emp.management.service.DailyWorkReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = DailyWorkReportController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class DailyWorkReportControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private DailyWorkReportService reportService;

    private DailyWorkReportDTO sampleReport;
    private final String start = "2026-06-01";
    private final String end   = "2026-06-30";

    @BeforeEach
    void setUp() {
        sampleReport = DailyWorkReportDTO.builder()
                .id(1L).employeeId(2L).employeeName("John Doe")
                .reportDate(LocalDate.of(2026, 6, 1))
                .totalOfficeMinutes(480).activeMinutes(450).breakMinutes(30)
                .totalOfficeFormatted("8h 0m").build();
    }

    // ── GET /api/work-reports ─────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin@company.com", roles = "ADMIN")
    void getTeamReports_asAdmin_returns200() throws Exception {
        when(reportService.getTeamReports(anyString(), any(), any()))
                .thenReturn(List.of(sampleReport));

        mockMvc.perform(get("/api/work-reports")
                .param("start", start)
                .param("end", end))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employeeName").value("John Doe"));
    }

    @Test
    @WithMockUser(username = "mgr@company.com", roles = "MANAGER")
    void getTeamReports_asManager_returns200() throws Exception {
        when(reportService.getTeamReports(anyString(), any(), any()))
                .thenReturn(List.of(sampleReport));

        mockMvc.perform(get("/api/work-reports")
                .param("start", start)
                .param("end", end))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getTeamReports_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/work-reports")
                .param("start", start)
                .param("end", end))
                .andExpect(status().isForbidden());
    }

    @Test
    void getTeamReports_unauthenticated_returns401or403() throws Exception {
        mockMvc.perform(get("/api/work-reports")
                .param("start", start)
                .param("end", end))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s != 401 && s != 403) throw new AssertionError("Expected 401 or 403, got: " + s);
                });
    }

    // ── GET /api/work-reports/my ──────────────────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void getMyReports_asEmployee_returns200() throws Exception {
        when(reportService.getMyReports(anyString(), any(), any()))
                .thenReturn(List.of(sampleReport));

        mockMvc.perform(get("/api/work-reports/my")
                .param("start", start)
                .param("end", end))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].totalOfficeMinutes").value(480));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getMyReports_asAdmin_returns200() throws Exception {
        when(reportService.getMyReports(anyString(), any(), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/work-reports/my")
                .param("start", start)
                .param("end", end))
                .andExpect(status().isOk());
    }

    @Test
    void getMyReports_unauthenticated_returns401or403() throws Exception {
        mockMvc.perform(get("/api/work-reports/my")
                .param("start", start)
                .param("end", end))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s != 401 && s != 403) throw new AssertionError("Expected 401 or 403, got: " + s);
                });
    }

    // ── POST /api/work-reports/generate ──────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void generateReports_asAdmin_returns200() throws Exception {
        doNothing().when(reportService).generateAllReportsForDate(any());

        mockMvc.perform(post("/api/work-reports/generate")
                .param("date", "2026-06-01"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "HR")
    void generateReports_asHr_returns200() throws Exception {
        doNothing().when(reportService).generateAllReportsForDate(any());

        mockMvc.perform(post("/api/work-reports/generate")
                .param("date", "2026-06-01"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void generateReports_asEmployee_returns403() throws Exception {
        mockMvc.perform(post("/api/work-reports/generate")
                .param("date", "2026-06-01"))
                .andExpect(status().isForbidden());
    }

    // ── POST /api/work-reports/generate/range ────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void generateReportsForRange_asAdmin_returns200() throws Exception {
        doNothing().when(reportService).generateAllReportsForRange(any(), any());

        mockMvc.perform(post("/api/work-reports/generate/range")
                .param("start", start)
                .param("end", end))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void generateReportsForRange_asManager_returns403() throws Exception {
        mockMvc.perform(post("/api/work-reports/generate/range")
                .param("start", start)
                .param("end", end))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/work-reports/export ──────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin@company.com", roles = "ADMIN")
    void exportCsv_asAdmin_returns200WithCsv() throws Exception {
        byte[] csvData = "id,name,date\n1,John,2026-06-01".getBytes();
        when(reportService.exportToCsv(anyString(), any(), any())).thenReturn(csvData);

        mockMvc.perform(get("/api/work-reports/export")
                .param("start", start)
                .param("end", end))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=work-report-2026-06-01-to-2026-06-30.csv"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void exportCsv_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/work-reports/export")
                .param("start", start)
                .param("end", end))
                .andExpect(status().isForbidden());
    }
}
