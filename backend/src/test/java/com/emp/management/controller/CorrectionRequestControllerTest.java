package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.dto.AttendanceSessionDTO;
import com.emp.management.dto.AuditLogDTO;
import com.emp.management.dto.CorrectionRequestDTO;
import com.emp.management.exception.GlobalExceptionHandler;
import com.emp.management.security.JwtAuthFilter;
import com.emp.management.service.CorrectionRequestService;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CorrectionRequestController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class CorrectionRequestControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private CorrectionRequestService correctionRequestService;

    private CorrectionRequestDTO sampleRequest;
    private AttendanceSessionDTO sampleSession;

    @BeforeEach
    void setUp() {
        sampleRequest = CorrectionRequestDTO.builder()
                .id(1L).employeeId(2L).employeeName("John Doe")
                .workDate(LocalDate.now()).loginTime(LocalTime.of(9, 0))
                .requestedLogoutTime(LocalTime.of(18, 0))
                .reason("Forgot to check out").status("PENDING").build();

        sampleSession = AttendanceSessionDTO.builder()
                .id(5L).employeeId(2L).workDate(LocalDate.now())
                .loginTime(LocalTime.of(9, 0)).build();
    }

    // ── GET /api/corrections/pending-sessions ─────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void getPendingSessions_asEmployee_returns200() throws Exception {
        when(correctionRequestService.getPendingSessions("emp@company.com"))
                .thenReturn(List.of(sampleSession));

        mockMvc.perform(get("/api/corrections/pending-sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5));
    }

    @Test
    @WithMockUser(username = "mgr@company.com", roles = "MANAGER")
    void getPendingSessions_asManager_returns200() throws Exception {
        when(correctionRequestService.getPendingSessions("mgr@company.com"))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/corrections/pending-sessions"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getPendingSessions_asAdmin_returns403() throws Exception {
        // ADMIN is not in the allowed roles list
        mockMvc.perform(get("/api/corrections/pending-sessions"))
                .andExpect(status().isForbidden());
    }

    // ── POST /api/corrections ─────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void submit_asEmployee_returns200() throws Exception {
        when(correctionRequestService.submit(anyString(), anyLong(), anyString(), anyString(), any()))
                .thenReturn(sampleRequest);

        mockMvc.perform(post("/api/corrections")
                .param("sessionId", "5")
                .param("requestedLogoutTime", "18:00:00")
                .param("reason", "Forgot to check out"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    // ── GET /api/corrections/my ───────────────────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void getMyRequests_asEmployee_returns200() throws Exception {
        when(correctionRequestService.getMyRequests("emp@company.com"))
                .thenReturn(List.of(sampleRequest));

        mockMvc.perform(get("/api/corrections/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employeeName").value("John Doe"));
    }

    @Test
    void getMyRequests_unauthenticated_returns401or403() throws Exception {
        mockMvc.perform(get("/api/corrections/my"))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s != 401 && s != 403) throw new AssertionError("Expected 401 or 403, got: " + s);
                });
    }

    // ── GET /api/corrections/pending/team ─────────────────────────────────────

    @Test
    @WithMockUser(username = "mgr@company.com", roles = "MANAGER")
    void getPendingTeam_asManager_returns200() throws Exception {
        when(correctionRequestService.getPendingForTeam("mgr@company.com"))
                .thenReturn(List.of(sampleRequest));

        mockMvc.perform(get("/api/corrections/pending/team"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getPendingTeam_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/corrections/pending/team"))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/corrections/pending ──────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllPending_asAdmin_returns200() throws Exception {
        when(correctionRequestService.getAllPending()).thenReturn(List.of(sampleRequest));

        mockMvc.perform(get("/api/corrections/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getAllPending_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/corrections/pending"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void getAllPending_asManager_returns403() throws Exception {
        // MANAGER is not in allowed roles for getAllPending (only ADMIN, DIRECTOR, HR)
        mockMvc.perform(get("/api/corrections/pending"))
                .andExpect(status().isForbidden());
    }

    // ── PUT /api/corrections/{id}/approve ─────────────────────────────────────

    @Test
    @WithMockUser(username = "mgr@company.com", roles = "MANAGER")
    void approve_asManager_returns200() throws Exception {
        CorrectionRequestDTO approved = CorrectionRequestDTO.builder()
                .id(1L).status("APPROVED").build();
        when(correctionRequestService.approve(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(approved);

        mockMvc.perform(put("/api/corrections/1/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"logoutTime\":\"18:00:00\",\"comment\":\"Approved\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void approve_asEmployee_returns403() throws Exception {
        mockMvc.perform(put("/api/corrections/1/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"logoutTime\":\"18:00:00\"}"))
                .andExpect(status().isForbidden());
    }

    // ── PUT /api/corrections/{id}/reject ─────────────────────────────────────

    @Test
    @WithMockUser(username = "admin@company.com", roles = "ADMIN")
    void reject_asAdmin_returns200() throws Exception {
        CorrectionRequestDTO rejected = CorrectionRequestDTO.builder()
                .id(1L).status("REJECTED").build();
        when(correctionRequestService.reject(anyLong(), anyString(), anyString()))
                .thenReturn(rejected);

        mockMvc.perform(put("/api/corrections/1/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\"Insufficient evidence\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void reject_asEmployee_returns403() throws Exception {
        mockMvc.perform(put("/api/corrections/1/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\"N/A\"}"))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/corrections/audit-logs ──────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAuditLogs_asAdmin_returns200() throws Exception {
        AuditLogDTO log = AuditLogDTO.builder()
                .id(1L).action("APPROVED").approverName("admin@company.com").build();
        when(correctionRequestService.getAuditLogs()).thenReturn(List.of(log));

        mockMvc.perform(get("/api/corrections/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("APPROVED"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getAuditLogs_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/corrections/audit-logs"))
                .andExpect(status().isForbidden());
    }
}
