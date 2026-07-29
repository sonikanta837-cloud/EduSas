package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.dto.PipCommentDTO;
import com.emp.management.dto.PipDTO;
import com.emp.management.dto.PipGoalDTO;
import com.emp.management.dto.PipOutcomeDTO;
import com.emp.management.exception.BadRequestException;
import com.emp.management.exception.GlobalExceptionHandler;
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
        samplePip = PipDTO.builder().id(100L).employeeId(2L).employeeName("John Doe").title("Improve delivery")
                .status("ACTIVE").build();
    }

    // ── GET /api/pip ──────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void getPips_asEmployee_returns200() throws Exception {
        when(pipService.getPips("emp@company.com")).thenReturn(List.of(samplePip));

        mockMvc.perform(get("/api/pip"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void getPips_unauthenticated_returns401or403() throws Exception {
        mockMvc.perform(get("/api/pip"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assert status == 401 || status == 403;
                });
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
                .andExpect(jsonPath("$.title").value("Improve delivery"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void createPip_asEmployee_returns403() throws Exception {
        mockMvc.perform(post("/api/pip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(samplePip)))
                .andExpect(status().isForbidden());

        verify(pipService, never()).createPip(any(), any());
    }

    @Test
    @WithMockUser(username = "mgr@company.com", roles = "MANAGER")
    void createPip_endBeforeStart_returns400() throws Exception {
        when(pipService.createPip(any(), anyString()))
                .thenThrow(new BadRequestException("End date must be after start date"));

        mockMvc.perform(post("/api/pip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(samplePip)))
                .andExpect(status().isBadRequest());
    }

    // ── DELETE /api/pip/{id} ──────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void deletePip_asAdmin_returns204() throws Exception {
        doNothing().when(pipService).deletePip(eq(100L), anyString());

        mockMvc.perform(delete("/api/pip/100"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void deletePip_asManager_returns403() throws Exception {
        // Delete is narrower than create/update — Manager can create a PIP but not delete one
        mockMvc.perform(delete("/api/pip/100"))
                .andExpect(status().isForbidden());

        verify(pipService, never()).deletePip(any(), any());
    }

    @Test
    @WithMockUser(roles = "HR")
    void deletePip_asHR_returns403() throws Exception {
        mockMvc.perform(delete("/api/pip/100"))
                .andExpect(status().isForbidden());
    }

    // ── goals ─────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "mgr@company.com", roles = "MANAGER")
    void addGoal_asManager_returns201() throws Exception {
        PipGoalDTO goalDto = PipGoalDTO.builder().title("Reduce bugs").build();
        when(pipService.addGoal(eq(100L), any(), eq("mgr@company.com"))).thenReturn(goalDto);

        mockMvc.perform(post("/api/pip/100/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(goalDto)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void addGoal_asEmployee_returns403() throws Exception {
        mockMvc.perform(post("/api/pip/100/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(PipGoalDTO.builder().build())))
                .andExpect(status().isForbidden());
    }

    // ── comments — all roles including EMPLOYEE ──────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void addComment_asEmployee_returns201() throws Exception {
        PipCommentDTO commentDto = PipCommentDTO.builder().id(1L).content("Thanks for the support").build();
        when(pipService.addComment(eq(100L), eq("Thanks for the support"), eq("emp@company.com")))
                .thenReturn(commentDto);

        mockMvc.perform(post("/api/pip/100/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "Thanks for the support"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Thanks for the support"));
    }

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void addComment_blankContent_returns400FromService() throws Exception {
        when(pipService.addComment(eq(100L), eq(""), eq("emp@company.com")))
                .thenThrow(new BadRequestException("Comment content cannot be empty"));

        mockMvc.perform(post("/api/pip/100/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", ""))))
                .andExpect(status().isBadRequest());
    }

    // ── outcome ───────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "mgr@company.com", roles = "MANAGER")
    void setOutcome_valid_returns200() throws Exception {
        PipOutcomeDTO dto = PipOutcomeDTO.builder().status("COMPLETED").finalNotes("Good job").build();
        PipDTO completed = PipDTO.builder().id(100L).status("COMPLETED").build();
        when(pipService.setOutcome(eq(100L), any(), eq("mgr@company.com"))).thenReturn(completed);

        mockMvc.perform(put("/api/pip/100/outcome")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void setOutcome_asEmployee_returns403() throws Exception {
        PipOutcomeDTO dto = PipOutcomeDTO.builder().status("COMPLETED").build();

        mockMvc.perform(put("/api/pip/100/outcome")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "mgr@company.com", roles = "MANAGER")
    void setOutcome_invalidStatus_returns400() throws Exception {
        PipOutcomeDTO dto = PipOutcomeDTO.builder().status("CANCELLED").build();
        when(pipService.setOutcome(eq(100L), any(), anyString()))
                .thenThrow(new BadRequestException("Invalid outcome status: CANCELLED"));

        mockMvc.perform(put("/api/pip/100/outcome")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/pip/employee/{empId} ─────────────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void getPipsByEmployee_asEmployee_returns200() throws Exception {
        when(pipService.getPipsByEmployee(eq(2L), eq("emp@company.com"))).thenReturn(List.of(samplePip));

        mockMvc.perform(get("/api/pip/employee/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ── GET /api/pip/{id} ──────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void getPipDetail_returns200() throws Exception {
        when(pipService.getPipDetail(eq(100L), eq("emp@company.com"))).thenReturn(samplePip);

        mockMvc.perform(get("/api/pip/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100));
    }
}
