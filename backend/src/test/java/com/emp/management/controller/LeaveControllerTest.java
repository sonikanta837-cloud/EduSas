package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.dto.LeaveDTO;
import com.emp.management.entity.LeaveStatus;
import com.emp.management.exception.GlobalExceptionHandler;
import com.emp.management.repository.LeaveRepository;
import com.emp.management.security.JwtAuthFilter;
import com.emp.management.service.LeaveService;
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
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = LeaveController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class LeaveControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private LeaveService leaveService;
    @MockBean private LeaveRepository leaveRepository;

    private LeaveDTO sampleLeave;

    @BeforeEach
    void setUp() {
        sampleLeave = LeaveDTO.builder()
                .id(10L).employeeId(2L).employeeName("John Doe")
                .leaveType("Annual Leave")
                .startDate(LocalDate.now().plusDays(1)).endDate(LocalDate.now().plusDays(3))
                .totalDays(3).reason("Trip").status(LeaveStatus.PENDING)
                .build();
    }

    private LeaveDTO validRequestDTO() {
        return LeaveDTO.builder()
                .leaveType("Annual Leave")
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(3))
                .reason("Trip")
                .build();
    }

    // ── POST /api/leaves/apply/{employeeId} ──────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void applyLeave_validRequest_returns201() throws Exception {
        when(leaveService.applyLeave(eq(2L), any(), eq("emp@company.com"))).thenReturn(sampleLeave);

        mockMvc.perform(post("/api/leaves/apply/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequestDTO())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.leaveType").value("Annual Leave"));
    }

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void applyLeave_blankLeaveType_returns400() throws Exception {
        LeaveDTO dto = validRequestDTO();
        dto.setLeaveType("");

        mockMvc.perform(post("/api/leaves/apply/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void applyLeave_missingStartDate_returns400() throws Exception {
        LeaveDTO dto = validRequestDTO();
        dto.setStartDate(null);

        mockMvc.perform(post("/api/leaves/apply/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void applyLeave_unauthenticated_returns401or403() throws Exception {
        mockMvc.perform(post("/api/leaves/apply/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequestDTO())))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assert status == 401 || status == 403;
                });
    }

    // ── GET /api/leaves/manager/{managerId}/pending ──────────────────────────

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getPendingForManager_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/leaves/manager/1/pending"))
                .andExpect(status().isForbidden());

        verify(leaveService, never()).getPendingLeavesForManager(any());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void getPendingForManager_asManager_returns200() throws Exception {
        when(leaveService.getPendingLeavesForManager(1L)).thenReturn(List.of(sampleLeave));

        mockMvc.perform(get("/api/leaves/manager/1/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(roles = "HR")
    void getPendingForManager_asHR_returns200() throws Exception {
        when(leaveService.getPendingLeavesForManager(1L)).thenReturn(List.of(sampleLeave));

        mockMvc.perform(get("/api/leaves/manager/1/pending"))
                .andExpect(status().isOk());
    }

    // ── GET /api/leaves (org-wide) ────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "MANAGER")
    void getAllLeaves_asManager_returns403() throws Exception {
        mockMvc.perform(get("/api/leaves"))
                .andExpect(status().isForbidden());

        verify(leaveService, never()).getAllLeaves();
    }

    @Test
    @WithMockUser(roles = "HR")
    void getAllLeaves_asHR_returns200() throws Exception {
        when(leaveService.getAllLeaves()).thenReturn(List.of(sampleLeave));

        mockMvc.perform(get("/api/leaves"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllLeaves_asAdmin_returns200() throws Exception {
        when(leaveService.getAllLeaves()).thenReturn(List.of(sampleLeave));

        mockMvc.perform(get("/api/leaves"))
                .andExpect(status().isOk());
    }

    // ── PATCH /api/leaves/{leaveId}/action ───────────────────────────────────

    @Test
    @WithMockUser(username = "mgr@company.com", roles = "MANAGER")
    void processLeave_validApproval_returns200() throws Exception {
        sampleLeave.setStatus(LeaveStatus.APPROVED);
        LeaveDTO approved = sampleLeave;
        when(leaveService.updateLeaveStatus(eq(10L), eq("mgr@company.com"), eq(LeaveStatus.APPROVED), anyString()))
                .thenReturn(approved);

        mockMvc.perform(patch("/api/leaves/10/action")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "APPROVED", "comment", "OK"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void processLeave_blankStatus_returns400() throws Exception {
        mockMvc.perform(patch("/api/leaves/10/action")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", ""))))
                .andExpect(status().isBadRequest());

        verify(leaveService, never()).updateLeaveStatus(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void processLeave_missingStatusKey_returns400() throws Exception {
        mockMvc.perform(patch("/api/leaves/10/action")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("comment", "no status field"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void processLeave_invalidStatusEnum_returns400() throws Exception {
        mockMvc.perform(patch("/api/leaves/10/action")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "CANCELLED"))))
                .andExpect(status().isBadRequest());

        verify(leaveService, never()).updateLeaveStatus(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(username = "mgr@company.com", roles = "MANAGER")
    void processLeave_lowercaseStatus_isCaseInsensitive() throws Exception {
        sampleLeave.setStatus(LeaveStatus.APPROVED);
        LeaveDTO approved = sampleLeave;
        when(leaveService.updateLeaveStatus(eq(10L), eq("mgr@company.com"), eq(LeaveStatus.APPROVED), anyString()))
                .thenReturn(approved);

        mockMvc.perform(patch("/api/leaves/10/action")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "approved"))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void processLeave_asEmployee_returns403() throws Exception {
        mockMvc.perform(patch("/api/leaves/10/action")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "APPROVED"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HR")
    void processLeave_asHR_returns403() throws Exception {
        // HR can view all leaves but is not in the decision-action role list
        mockMvc.perform(patch("/api/leaves/10/action")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "APPROVED"))))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/leaves/public-holidays ───────────────────────────────────────

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getPublicHolidays_anyAuthenticatedRole_returns200() throws Exception {
        when(leaveRepository.findDistinctPublicHolidays()).thenReturn(List.of());

        mockMvc.perform(get("/api/leaves/public-holidays"))
                .andExpect(status().isOk());
    }

    // ── DELETE /api/leaves/{leaveId} ─────────────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void deleteLeave_returns204() throws Exception {
        doNothing().when(leaveService).deleteLeave(10L, "emp@company.com");

        mockMvc.perform(delete("/api/leaves/10"))
                .andExpect(status().isNoContent());
    }

    // ── GET /api/leaves/employee/{employeeId} ────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void getMyLeaves_returns200WithList() throws Exception {
        when(leaveService.getMyLeaves(2L, "emp@company.com")).thenReturn(List.of(sampleLeave));

        mockMvc.perform(get("/api/leaves/employee/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
