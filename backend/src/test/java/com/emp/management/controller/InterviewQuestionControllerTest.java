package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.dto.InterviewQuestionDTO;
import com.emp.management.entity.QuestionDifficulty;
import com.emp.management.entity.QuestionType;
import com.emp.management.exception.GlobalExceptionHandler;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.security.JwtAuthFilter;
import com.emp.management.service.InterviewQuestionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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

@WebMvcTest(controllers = InterviewQuestionController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class InterviewQuestionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private InterviewQuestionService service;

    private InterviewQuestionDTO sampleQuestion;

    @BeforeEach
    void setUp() {
        sampleQuestion = InterviewQuestionDTO.builder()
                .id(1L).questionText("Explain SOLID").technology("Java")
                .difficulty(QuestionDifficulty.MEDIUM).questionType(QuestionType.TEXT).build();
    }

    // ── GET /api/interview-questions ─────────────────────────────────────────

    @Test
    @WithMockUser(roles = "MANAGER")
    void getAll_asManager_returns200() throws Exception {
        when(service.getAll()).thenReturn(List.of(sampleQuestion));

        mockMvc.perform(get("/api/interview-questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getAll_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/interview-questions"))
                .andExpect(status().isForbidden());
    }

    // ── POST /api/interview-questions ────────────────────────────────────────

    @Test
    @WithMockUser(username = "hr@company.com", roles = "HR")
    void create_asHR_returns200() throws Exception {
        when(service.create(any(), eq("hr@company.com"))).thenReturn(sampleQuestion);

        mockMvc.perform(post("/api/interview-questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleQuestion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionText").value("Explain SOLID"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void create_asEmployee_returns403() throws Exception {
        mockMvc.perform(post("/api/interview-questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleQuestion)))
                .andExpect(status().isForbidden());

        verify(service, never()).create(any(), any());
    }

    // ── DELETE /api/interview-questions/{id} — narrower than create ─────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_asAdmin_returns204() throws Exception {
        doNothing().when(service).delete(1L);

        mockMvc.perform(delete("/api/interview-questions/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void delete_asManager_returns403() throws Exception {
        // Manager can create/update/toggle questions but not delete them
        mockMvc.perform(delete("/api/interview-questions/1"))
                .andExpect(status().isForbidden());

        verify(service, never()).delete(any());
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_MANAGER")
    void delete_asAssistantManager_returns403() throws Exception {
        mockMvc.perform(delete("/api/interview-questions/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Question not found: 999")).when(service).delete(999L);

        mockMvc.perform(delete("/api/interview-questions/999"))
                .andExpect(status().isNotFound());
    }

    // ── PATCH /api/interview-questions/{id}/toggle-status ────────────────────

    @Test
    @WithMockUser(roles = "MANAGER")
    void toggleStatus_asManager_returns200() throws Exception {
        when(service.toggleStatus(1L)).thenReturn(sampleQuestion);

        mockMvc.perform(patch("/api/interview-questions/1/toggle-status"))
                .andExpect(status().isOk());
    }

    // ── GET metadata endpoints ────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "HR")
    void getTechnologies_returns200() throws Exception {
        when(service.getTechnologies()).thenReturn(List.of("Java", "React"));

        mockMvc.perform(get("/api/interview-questions/technologies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @WithMockUser(roles = "HR")
    void getStats_returns200() throws Exception {
        when(service.getStats()).thenReturn(Map.of("total", 10L, "active", 8L, "inactive", 2L));

        mockMvc.perform(get("/api/interview-questions/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(10));
    }

    // ── POST /api/interview-questions/import ──────────────────────────────────

    @Test
    @WithMockUser(username = "hr@company.com", roles = "HR")
    void importExcel_asHR_returns200() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "questions.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1, 2, 3});
        when(service.importFromExcel(any(), eq("hr@company.com")))
                .thenReturn(Map.of("saved", 5, "failed", 0, "errors", List.of()));

        mockMvc.perform(multipart("/api/interview-questions/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(5));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void importExcel_asEmployee_returns403() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "questions.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/interview-questions/import").file(file))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/interview-questions/export ───────────────────────────────────

    @Test
    @WithMockUser(roles = "HR")
    void exportExcel_returns200WithAttachmentHeader() throws Exception {
        when(service.exportToExcel()).thenReturn(new byte[]{1, 2, 3});

        mockMvc.perform(get("/api/interview-questions/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"interview-questions.xlsx\""));
    }

    // ── GET /api/interview-questions/{id} ─────────────────────────────────────

    @Test
    @WithMockUser(roles = "HR")
    void getById_returns200() throws Exception {
        when(service.getById(1L)).thenReturn(sampleQuestion);

        mockMvc.perform(get("/api/interview-questions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(roles = "HR")
    void getById_notFound_returns404() throws Exception {
        when(service.getById(999L)).thenThrow(new ResourceNotFoundException("Question not found: 999"));

        mockMvc.perform(get("/api/interview-questions/999"))
                .andExpect(status().isNotFound());
    }
}
