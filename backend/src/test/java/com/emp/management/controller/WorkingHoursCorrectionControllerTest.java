package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.dto.WorkingHoursCorrectionRequestDTO;
import com.emp.management.dto.WorkingHoursCorrectionSubmitRequest;
import com.emp.management.entity.CorrectionReason;
import com.emp.management.entity.CorrectionRequestStatus;
import com.emp.management.exception.BadRequestException;
import com.emp.management.exception.GlobalExceptionHandler;
import com.emp.management.security.JwtAuthFilter;
import com.emp.management.service.WorkingHoursCorrectionService;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = WorkingHoursCorrectionController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class WorkingHoursCorrectionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private WorkingHoursCorrectionService correctionService;

    private WorkingHoursCorrectionRequestDTO sampleRequest;

    @BeforeEach
    void setUp() {
        sampleRequest = WorkingHoursCorrectionRequestDTO.builder()
                .id(500L).employeeId(2L).employeeName("John Doe")
                .workDate(LocalDate.now()).reason(CorrectionReason.FORGOT_START_BREAK)
                .status(CorrectionRequestStatus.PENDING)
                .build();
    }

    // ── POST /api/working-hours-corrections/apply/{employeeId} ──────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void apply_validRequest_returns201() throws Exception {
        WorkingHoursCorrectionSubmitRequest req = WorkingHoursCorrectionSubmitRequest.builder()
                .workDate(LocalDate.now()).reason(CorrectionReason.OTHER).reasonComments("forgot to log break")
                .build();
        when(correctionService.submit(eq(2L), any(), eq("emp@company.com"))).thenReturn(sampleRequest);

        mockMvc.perform(post("/api/working-hours-corrections/apply/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employeeId").value(2));
    }

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void apply_missingWorkDate_returns400() throws Exception {
        WorkingHoursCorrectionSubmitRequest req = WorkingHoursCorrectionSubmitRequest.builder()
                .reason(CorrectionReason.OTHER).reasonComments("x").build();

        mockMvc.perform(post("/api/working-hours-corrections/apply/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void apply_missingReason_returns400() throws Exception {
        WorkingHoursCorrectionSubmitRequest req = WorkingHoursCorrectionSubmitRequest.builder()
                .workDate(LocalDate.now()).build();

        mockMvc.perform(post("/api/working-hours-corrections/apply/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void apply_futureDate_returns400FromService() throws Exception {
        WorkingHoursCorrectionSubmitRequest req = WorkingHoursCorrectionSubmitRequest.builder()
                .workDate(LocalDate.now().plusDays(1)).reason(CorrectionReason.OTHER).reasonComments("x").build();
        when(correctionService.submit(eq(2L), any(), anyString()))
                .thenThrow(new BadRequestException("Cannot submit a correction for a future date"));

        mockMvc.perform(post("/api/working-hours-corrections/apply/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/working-hours-corrections/manager/{managerId}/pending ──────

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getPendingForManager_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/working-hours-corrections/manager/1/pending"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HR")
    void getPendingForManager_asHR_returns200() throws Exception {
        // HR can view pending corrections though it cannot decide them
        when(correctionService.getPendingForManager(1L)).thenReturn(List.of(sampleRequest));

        mockMvc.perform(get("/api/working-hours-corrections/manager/1/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void getPendingForManager_asManager_returns200() throws Exception {
        when(correctionService.getPendingForManager(1L)).thenReturn(List.of(sampleRequest));

        mockMvc.perform(get("/api/working-hours-corrections/manager/1/pending"))
                .andExpect(status().isOk());
    }

    // ── GET /api/working-hours-corrections (org-wide) ────────────────────────

    @Test
    @WithMockUser(roles = "MANAGER")
    void getAll_asManager_returns403() throws Exception {
        mockMvc.perform(get("/api/working-hours-corrections"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HR")
    void getAll_asHR_returns200() throws Exception {
        when(correctionService.getAll()).thenReturn(List.of(sampleRequest));

        mockMvc.perform(get("/api/working-hours-corrections"))
                .andExpect(status().isOk());
    }

    // ── PATCH /api/working-hours-corrections/{requestId}/action ─────────────

    @Test
    @WithMockUser(username = "mgr@company.com", roles = "MANAGER")
    void processRequest_approve_asManager_returns200() throws Exception {
        WorkingHoursCorrectionRequestDTO approved = WorkingHoursCorrectionRequestDTO.builder()
                .id(500L).status(CorrectionRequestStatus.APPROVED).build();
        when(correctionService.decide(eq(500L), eq("mgr@company.com"), eq(CorrectionRequestStatus.APPROVED), anyString()))
                .thenReturn(approved);

        mockMvc.perform(patch("/api/working-hours-corrections/500/action")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "APPROVED", "comment", "OK"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @WithMockUser(roles = "HR")
    void processRequest_asHR_returns403_viewOnlyRoleExcludedFromDecision() throws Exception {
        mockMvc.perform(patch("/api/working-hours-corrections/500/action")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "APPROVED"))))
                .andExpect(status().isForbidden());

        verify(correctionService, never()).decide(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void processRequest_asEmployee_returns403() throws Exception {
        mockMvc.perform(patch("/api/working-hours-corrections/500/action")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "APPROVED"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void processRequest_blankStatus_returns400() throws Exception {
        mockMvc.perform(patch("/api/working-hours-corrections/500/action")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void processRequest_invalidStatusEnum_returns400() throws Exception {
        mockMvc.perform(patch("/api/working-hours-corrections/500/action")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "MAYBE"))))
                .andExpect(status().isBadRequest());

        verify(correctionService, never()).decide(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(username = "mgr@company.com", roles = "MANAGER")
    void processRequest_alreadyProcessed_returns400() throws Exception {
        when(correctionService.decide(eq(500L), anyString(), any(), anyString()))
                .thenThrow(new BadRequestException("Correction request already processed"));

        mockMvc.perform(patch("/api/working-hours-corrections/500/action")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "REJECTED"))))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/working-hours-corrections/{requestId}/audit-trail ──────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void getAuditTrail_returns200() throws Exception {
        when(correctionService.getAuditTrail(eq(500L), eq("emp@company.com"))).thenReturn(List.of());

        mockMvc.perform(get("/api/working-hours-corrections/500/audit-trail"))
                .andExpect(status().isOk());
    }

    // ── GET /api/working-hours-corrections/employee/{employeeId} ────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void getForEmployee_returns200WithHistory() throws Exception {
        when(correctionService.getMyRequests(eq(2L), eq("emp@company.com"))).thenReturn(List.of(sampleRequest));

        mockMvc.perform(get("/api/working-hours-corrections/employee/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
