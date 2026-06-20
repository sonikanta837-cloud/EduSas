package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.dto.LeaveUploadResult;
import com.emp.management.exception.GlobalExceptionHandler;
import com.emp.management.repository.LeaveRepository;
import com.emp.management.security.JwtAuthFilter;
import com.emp.management.service.LeaveUploadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = LeaveUploadController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class LeaveUploadControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private LeaveUploadService leaveUploadService;
    @MockBean private LeaveRepository leaveRepository;

    // ── POST /api/leave-upload ────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void upload_asAdmin_withFile_returns200() throws Exception {
        LeaveUploadResult result = LeaveUploadResult.builder()
                .totalRows(10).imported(9).skipped(1).errors(List.of("Row 5: invalid date")).build();
        when(leaveUploadService.processUpload(any(), any())).thenReturn(result);

        MockMultipartFile file = new MockMultipartFile(
                "file", "leaves.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "EXCEL_CONTENT".getBytes());

        mockMvc.perform(multipart("/api/leave-upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(10))
                .andExpect(jsonPath("$.imported").value(9));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void upload_emptyFile_returns400() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[0]);

        mockMvc.perform(multipart("/api/leave-upload").file(emptyFile))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void upload_serviceThrowsException_returns500() throws Exception {
        when(leaveUploadService.processUpload(any(), any()))
                .thenThrow(new RuntimeException("Parse error"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "bad.xlsx", "application/vnd.ms-excel", "BAD".getBytes());

        mockMvc.perform(multipart("/api/leave-upload").file(file))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void upload_asEmployee_returns403() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "leaves.xlsx", "application/vnd.ms-excel", "CONTENT".getBytes());

        mockMvc.perform(multipart("/api/leave-upload").file(file))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HR")
    void upload_asHr_returns403() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "leaves.xlsx", "application/vnd.ms-excel", "CONTENT".getBytes());

        mockMvc.perform(multipart("/api/leave-upload").file(file))
                .andExpect(status().isForbidden());
    }

    // ── POST /api/leave-upload/holiday ────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void addHoliday_asAdmin_returns200() throws Exception {
        LeaveUploadResult result = LeaveUploadResult.builder()
                .totalRows(1).imported(1).skipped(0).build();
        when(leaveUploadService.addSingleHoliday(any(), any(), any())).thenReturn(result);

        mockMvc.perform(post("/api/leave-upload/holiday")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Independence Day\",\"date\":\"2026-08-15\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void addHoliday_missingName_returns400() throws Exception {
        mockMvc.perform(post("/api/leave-upload/holiday")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"2026-08-15\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void addHoliday_missingDate_returns400() throws Exception {
        mockMvc.perform(post("/api/leave-upload/holiday")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Independence Day\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void addHoliday_invalidDateFormat_returns500() throws Exception {
        mockMvc.perform(post("/api/leave-upload/holiday")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Holiday\",\"date\":\"not-a-date\"}"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void addHoliday_asEmployee_returns403() throws Exception {
        mockMvc.perform(post("/api/leave-upload/holiday")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Holiday\",\"date\":\"2026-08-15\"}"))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/leave-upload/holidays ────────────────────────────────────────

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getUploadedHolidays_returns200() throws Exception {
        List<Object[]> holidays = new ArrayList<>();
        holidays.add(new Object[]{"Independence Day", java.sql.Date.valueOf("2026-08-15"), 50L});
        when(leaveRepository.findDistinctPublicHolidays()).thenReturn(holidays);

        mockMvc.perform(get("/api/leave-upload/holidays"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Independence Day"))
                .andExpect(jsonPath("$[0].employeeCount").value(50));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getUploadedHolidays_emptyList_returns200() throws Exception {
        when(leaveRepository.findDistinctPublicHolidays()).thenReturn(List.of());

        mockMvc.perform(get("/api/leave-upload/holidays"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── GET /api/leave-upload/holidays/employees ──────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getEmployeesForHoliday_returns200() throws Exception {
        List<Object[]> employees = new ArrayList<>();
        employees.add(new Object[]{"John", "Doe", "EMP10001", "Mumbai"});
        when(leaveRepository.findEmployeesForHoliday(any(), any())).thenReturn(employees);

        mockMvc.perform(get("/api/leave-upload/holidays/employees")
                .param("name", "Independence Day")
                .param("date", "2026-08-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].firstName").value("John"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getEmployeesForHoliday_invalidDate_returns400() throws Exception {
        mockMvc.perform(get("/api/leave-upload/holidays/employees")
                .param("name", "Holiday")
                .param("date", "not-a-date"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/leave-upload/template ───────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void downloadTemplate_asAdmin_returns200() throws Exception {
        byte[] templateBytes = "EXCEL_TEMPLATE".getBytes();
        when(leaveUploadService.generateTemplate()).thenReturn(templateBytes);

        mockMvc.perform(get("/api/leave-upload/template"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"leave_upload_template.xlsx\""));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void downloadTemplate_serviceThrowsException_returns500() throws Exception {
        when(leaveUploadService.generateTemplate())
                .thenThrow(new RuntimeException("Template generation failed"));

        mockMvc.perform(get("/api/leave-upload/template"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void downloadTemplate_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/leave-upload/template"))
                .andExpect(status().isForbidden());
    }
}
