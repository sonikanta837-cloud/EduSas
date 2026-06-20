package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.dto.ResumeParseResponse;
import com.emp.management.exception.GlobalExceptionHandler;
import com.emp.management.security.JwtAuthFilter;
import com.emp.management.service.ResumeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ResumeController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class ResumeControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private ResumeService resumeService;

    // ── POST /api/resume/parse ────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void parse_asAdmin_withPdf_returns200() throws Exception {
        ResumeParseResponse response = ResumeParseResponse.builder()
                .firstName("John").lastName("Doe").email("john.doe@example.com").build();
        when(resumeService.parseResume(any())).thenReturn(response);

        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "PDF_CONTENT".getBytes());

        mockMvc.perform(multipart("/api/resume/parse").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.email").value("john.doe@example.com"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void parse_emptyFile_returns400() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/api/resume/parse").file(emptyFile))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void parse_serviceThrowsIllegalArgument_returns400() throws Exception {
        when(resumeService.parseResume(any()))
                .thenThrow(new IllegalArgumentException("Unsupported file format"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.txt", "text/plain", "Some content".getBytes());

        mockMvc.perform(multipart("/api/resume/parse").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void parse_serviceThrowsException_returns500() throws Exception {
        when(resumeService.parseResume(any()))
                .thenThrow(new RuntimeException("PDF parsing failed"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "CORRUPTED".getBytes());

        mockMvc.perform(multipart("/api/resume/parse").file(file))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void parse_asEmployee_returns403() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "PDF_CONTENT".getBytes());

        mockMvc.perform(multipart("/api/resume/parse").file(file))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void parse_asManager_returns403() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "PDF_CONTENT".getBytes());

        mockMvc.perform(multipart("/api/resume/parse").file(file))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HR")
    void parse_asHr_returns403() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "PDF_CONTENT".getBytes());

        mockMvc.perform(multipart("/api/resume/parse").file(file))
                .andExpect(status().isForbidden());
    }

    @Test
    void parse_unauthenticated_returns401or403() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "PDF_CONTENT".getBytes());

        mockMvc.perform(multipart("/api/resume/parse").file(file))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s != 401 && s != 403) throw new AssertionError("Expected 401 or 403, got: " + s);
                });
    }
}
