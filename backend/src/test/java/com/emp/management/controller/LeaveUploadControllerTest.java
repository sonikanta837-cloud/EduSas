package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.dto.LeaveUploadResult;
import com.emp.management.exception.GlobalExceptionHandler;
import com.emp.management.repository.LeaveRepository;
import com.emp.management.security.JwtAuthFilter;
import com.emp.management.service.LeaveUploadService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = LeaveUploadController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class LeaveUploadControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private LeaveUploadService leaveUploadService;
    @MockBean private LeaveRepository leaveRepository;

    @Test
    @WithMockUser(roles = "ADMIN")
    void upload_validFile_returns200() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "leaves.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1, 2, 3});
        when(leaveUploadService.processUpload(any(), eq("Pune")))
                .thenReturn(LeaveUploadResult.builder().totalRows(10).imported(8).skipped(2).errors(List.of()).build());

        mockMvc.perform(multipart("/api/leave-upload").file(file).param("location", "Pune"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(8));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void upload_emptyFile_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "leaves.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]);

        mockMvc.perform(multipart("/api/leave-upload").file(file))
                .andExpect(status().isBadRequest());

        verify(leaveUploadService, never()).processUpload(any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void upload_serviceThrows_returns500() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "leaves.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1, 2, 3});
        when(leaveUploadService.processUpload(any(), any())).thenThrow(new RuntimeException("corrupt file"));

        mockMvc.perform(multipart("/api/leave-upload").file(file))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @WithMockUser(roles = "HR")
    void upload_asHR_returns403() throws Exception {
        // Bulk leave/holiday upload is narrower than most HR-utility writes — HR is excluded
        MockMultipartFile file = new MockMultipartFile("file", "leaves.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1});

        mockMvc.perform(multipart("/api/leave-upload").file(file))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void addHoliday_valid_returns200() throws Exception {
        when(leaveUploadService.addSingleHoliday(eq("Diwali"), any(), isNull()))
                .thenReturn(LeaveUploadResult.builder().totalRows(1).imported(1).skipped(0).errors(List.of()).build());

        mockMvc.perform(post("/api/leave-upload/holiday")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Diwali", "date", "2026-11-08"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void addHoliday_missingName_returns400() throws Exception {
        mockMvc.perform(post("/api/leave-upload/holiday")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("date", "2026-11-08"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void addHoliday_invalidDateFormat_returns500() throws Exception {
        // Date parsing failure is caught by the generic catch-all, not surfaced as 400
        mockMvc.perform(post("/api/leave-upload/holiday")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Diwali", "date", "not-a-date"))))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getUploadedHolidays_anyAuthenticatedRole_returns200() throws Exception {
        when(leaveRepository.findDistinctPublicHolidays()).thenReturn(List.of());

        mockMvc.perform(get("/api/leave-upload/holidays"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getEmployeesForHoliday_invalidDate_returns400() throws Exception {
        mockMvc.perform(get("/api/leave-upload/holidays/employees")
                        .param("name", "Diwali").param("date", "not-a-date"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getEmployeesForHoliday_valid_returns200() throws Exception {
        when(leaveRepository.findEmployeesForHoliday(any(), eq("Diwali"))).thenReturn(List.of());

        mockMvc.perform(get("/api/leave-upload/holidays/employees")
                        .param("name", "Diwali").param("date", "2026-11-08"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void downloadTemplate_asAdmin_returns200WithAttachmentHeader() throws Exception {
        when(leaveUploadService.generateTemplate()).thenReturn(new byte[]{1, 2, 3});

        mockMvc.perform(get("/api/leave-upload/template"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"leave_upload_template.xlsx\""));
    }

    @Test
    @WithMockUser(roles = "HR")
    void downloadTemplate_asHR_returns403() throws Exception {
        mockMvc.perform(get("/api/leave-upload/template"))
                .andExpect(status().isForbidden());
    }
}
