package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.dto.PipCommentDTO;
import com.emp.management.dto.PipDTO;
import com.emp.management.dto.PipGoalDTO;
import com.emp.management.dto.PipOutcomeDTO;
import com.emp.management.dto.PipWeeklyReviewDTO;
import com.emp.management.exception.GlobalExceptionHandler;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.security.JwtAuthFilter;
import com.emp.management.service.PipService;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PipController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class PipControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private PipService pipService;

    private PipDTO samplePip;

    @BeforeEach
    void setUp() {
        samplePip = PipDTO.builder()
                .id(1L)
                .employeeId(2L)
                .title("Performance Improvement Plan")
                .status("ACTIVE")
                .build();
    }

    // ── GET /api/pip ──────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin@company.com", roles = "ADMIN")
    void getPips_asAdmin_returns200() throws Exception {
        when(pipService.getPips("admin@company.com")).thenReturn(List.of(samplePip));

        mockMvc.perform(get("/api/pip"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void getPips_asEmployee_returns200() throws Exception {
        when(pipService.getPips("emp@company.com")).thenReturn(List.of(samplePip));

        mockMvc.perform(get("/api/pip"))
                .andExpect(status().isOk());
    }

    @Test
    void getPips_unauthenticated_returns401or403() throws Exception {
        mockMvc.perform(get("/api/pip"))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s != 401 && s != 403) throw new AssertionError("Expected 401 or 403, got: " + s);
                });
    }

    // ── GET /api/pip/{id} ─────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin@company.com", roles = "ADMIN")
    void getPipDetail_existingId_returns200() throws Exception {
        when(pipService.getPipDetail(1L, "admin@company.com")).thenReturn(samplePip);

        mockMvc.perform(get("/api/pip/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Performance Improvement Plan"));
    }

    @Test
    @WithMockUser(username = "admin@company.com", roles = "ADMIN")
    void getPipDetail_notFound_returns404() throws Exception {
        when(pipService.getPipDetail(eq(999L), anyString()))
                .thenThrow(new ResourceNotFoundException("PIP", 999L));

        mockMvc.perform(get("/api/pip/999"))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/pip/employee/{id} ────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin@company.com", roles = "ADMIN")
    void getPipsByEmployee_returns200() throws Exception {
        when(pipService.getPipsByEmployee(2L, "admin@company.com")).thenReturn(List.of(samplePip));

        mockMvc.perform(get("/api/pip/employee/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ── POST /api/pip ─────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "mgr@company.com", roles = "MANAGER")
    void createPip_asManager_returns201() throws Exception {
        when(pipService.createPip(any(), eq("mgr@company.com"))).thenReturn(samplePip);

        mockMvc.perform(post("/api/pip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(samplePip)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void createPip_asEmployee_returns403() throws Exception {
        mockMvc.perform(post("/api/pip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(samplePip)))
                .andExpect(status().isForbidden());
    }

    // ── PUT /api/pip/{id} ─────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "mgr@company.com", roles = "MANAGER")
    void updatePip_asManager_returns200() throws Exception {
        when(pipService.updatePip(eq(1L), any(), eq("mgr@company.com"))).thenReturn(samplePip);

        mockMvc.perform(put("/api/pip/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(samplePip)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    // ── DELETE /api/pip/{id} ──────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin@company.com", roles = "ADMIN")
    void deletePip_asAdmin_returns204() throws Exception {
        doNothing().when(pipService).deletePip(1L, "admin@company.com");

        mockMvc.perform(delete("/api/pip/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void deletePip_asManager_returns403() throws Exception {
        mockMvc.perform(delete("/api/pip/1"))
                .andExpect(status().isForbidden());
    }

    // ── POST /api/pip/{id}/goals ──────────────────────────────────────────────

    @Test
    @WithMockUser(username = "mgr@company.com", roles = "MANAGER")
    void addGoal_asManager_returns201() throws Exception {
        PipGoalDTO goalDTO = PipGoalDTO.builder().id(1L).description("Improve code quality").build();
        when(pipService.addGoal(eq(1L), any(), eq("mgr@company.com"))).thenReturn(goalDTO);

        mockMvc.perform(post("/api/pip/1/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(goalDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Improve code quality"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void addGoal_asEmployee_returns403() throws Exception {
        mockMvc.perform(post("/api/pip/1/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // ── PUT /api/pip/{id}/goals/{goalId} ──────────────────────────────────────

    @Test
    @WithMockUser(username = "mgr@company.com", roles = "MANAGER")
    void updateGoal_returns200() throws Exception {
        PipGoalDTO goalDTO = PipGoalDTO.builder().id(1L).description("Updated goal").build();
        when(pipService.updateGoal(eq(1L), eq(1L), any(), eq("mgr@company.com"))).thenReturn(goalDTO);

        mockMvc.perform(put("/api/pip/1/goals/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(goalDTO)))
                .andExpect(status().isOk());
    }

    // ── DELETE /api/pip/{id}/goals/{goalId} ───────────────────────────────────

    @Test
    @WithMockUser(username = "mgr@company.com", roles = "MANAGER")
    void deleteGoal_returns204() throws Exception {
        doNothing().when(pipService).deleteGoal(1L, 1L, "mgr@company.com");

        mockMvc.perform(delete("/api/pip/1/goals/1"))
                .andExpect(status().isNoContent());
    }

    // ── POST /api/pip/{id}/reviews ────────────────────────────────────────────

    @Test
    @WithMockUser(username = "mgr@company.com", roles = "MANAGER")
    void addReview_returns201() throws Exception {
        PipWeeklyReviewDTO reviewDTO = PipWeeklyReviewDTO.builder().id(1L).overallProgress("Good progress").build();
        when(pipService.addWeeklyReview(eq(1L), any(), eq("mgr@company.com"))).thenReturn(reviewDTO);

        mockMvc.perform(post("/api/pip/1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.overallProgress").value("Good progress"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void addReview_asEmployee_returns403() throws Exception {
        mockMvc.perform(post("/api/pip/1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // ── POST /api/pip/{id}/comments ───────────────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void addComment_asEmployee_returns201() throws Exception {
        PipCommentDTO commentDTO = PipCommentDTO.builder().id(1L).content("Working hard").build();
        when(pipService.addComment(eq(1L), eq("Working hard"), eq("emp@company.com"))).thenReturn(commentDTO);

        mockMvc.perform(post("/api/pip/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "Working hard"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Working hard"));
    }

    // ── PUT /api/pip/{id}/outcome ─────────────────────────────────────────────

    @Test
    @WithMockUser(username = "mgr@company.com", roles = "MANAGER")
    void setOutcome_returns200() throws Exception {
        PipOutcomeDTO outcomeDTO = PipOutcomeDTO.builder().status("COMPLETED").build();
        when(pipService.setOutcome(eq(1L), any(), eq("mgr@company.com"))).thenReturn(samplePip);

        mockMvc.perform(put("/api/pip/1/outcome")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(outcomeDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void setOutcome_asEmployee_returns403() throws Exception {
        mockMvc.perform(put("/api/pip/1/outcome")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }
}
