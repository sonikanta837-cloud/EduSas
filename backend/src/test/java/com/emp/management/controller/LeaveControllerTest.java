package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.dto.LeaveDTO;
import com.emp.management.entity.LeaveStatus;
import com.emp.management.exception.BadRequestException;
import com.emp.management.exception.GlobalExceptionHandler;
import com.emp.management.exception.ResourceNotFoundException;
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
    @MockBean  private LeaveService leaveService;
    @MockBean  private LeaveRepository leaveRepository;

    private LeaveDTO sampleLeave;

    @BeforeEach
    void setUp() {
        sampleLeave = LeaveDTO.builder()
                .id(1L)
                .employeeId(2L)
                .employeeName("John Doe")
                .leaveType("Annual Leave")
                .startDate(LocalDate.now().plusDays(7))
                .endDate(LocalDate.now().plusDays(9))
                .totalDays(3)
                .reason("Vacation")
                .status(LeaveStatus.PENDING)
                .build();
    }

    // ── POST /api/leaves/apply/{employeeId} ──────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void applyLeave_validRequest_returns201() throws Exception {
        when(leaveService.applyLeave(eq(2L), any(), anyString())).thenReturn(sampleLeave);

        mockMvc.perform(post("/api/leaves/apply/2")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(sampleLeave)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.leaveType").value("Annual Leave"));
    }

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void applyLeave_missingLeaveType_returns400() throws Exception {
        LeaveDTO invalid = LeaveDTO.builder()
                .startDate(LocalDate.now().plusDays(7))
                .endDate(LocalDate.now().plusDays(9))
                .build(); // leaveType is null

        mockMvc.perform(post("/api/leaves/apply/2")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void applyLeave_overlappingDates_returns400() throws Exception {
        when(leaveService.applyLeave(eq(2L), any(), anyString()))
                .thenThrow(new BadRequestException("already have a leave application"));

        mockMvc.perform(post("/api/leaves/apply/2")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(sampleLeave)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void applyLeave_unauthenticated_returns401or403() throws Exception {
        mockMvc.perform(post("/api/leaves/apply/2")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(sampleLeave)))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assert status == 401 || status == 403;
                });
    }

    // ── GET /api/leaves/employee/{employeeId} ────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void getMyLeaves_returnsLeaveList() throws Exception {
        when(leaveService.getMyLeaves(eq(2L), anyString())).thenReturn(List.of(sampleLeave));

        mockMvc.perform(get("/api/leaves/employee/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].leaveType").value("Annual Leave"));
    }

    // ── GET /api/leaves (admin only) ─────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllLeaves_asAdmin_returns200() throws Exception {
        when(leaveService.getAllLeaves()).thenReturn(List.of(sampleLeave));

        mockMvc.perform(get("/api/leaves"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getAllLeaves_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/leaves"))
                .andExpect(status().isForbidden());
    }

    // ── PATCH /api/leaves/{leaveId}/action ───────────────────────────────────

    @Test
    @WithMockUser(username = "mgr@company.com", roles = "MANAGER")
    void processLeave_approveAction_returns200() throws Exception {
        when(leaveService.updateLeaveStatus(eq(1L), anyString(), eq(LeaveStatus.APPROVED), anyString()))
                .thenReturn(sampleLeave);

        mockMvc.perform(patch("/api/leaves/1/action")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("status", "APPROVED", "comment", "Looks good"))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "mgr@company.com", roles = "MANAGER")
    void processLeave_rejectAction_returns200() throws Exception {
        sampleLeave.setStatus(LeaveStatus.REJECTED);
        when(leaveService.updateLeaveStatus(eq(1L), anyString(), eq(LeaveStatus.REJECTED), anyString()))
                .thenReturn(sampleLeave);

        mockMvc.perform(patch("/api/leaves/1/action")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("status", "REJECTED", "comment", "Not approved"))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "mgr@company.com", roles = "MANAGER")
    void processLeave_missingStatus_returns400() throws Exception {
        mockMvc.perform(patch("/api/leaves/1/action")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("comment", "ok"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "mgr@company.com", roles = "MANAGER")
    void processLeave_invalidStatus_returns400() throws Exception {
        mockMvc.perform(patch("/api/leaves/1/action")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("status", "INVALID_STATUS"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void processLeave_asEmployee_returns403() throws Exception {
        mockMvc.perform(patch("/api/leaves/1/action")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("status", "APPROVED"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "mgr@company.com", roles = "MANAGER")
    void processLeave_alreadyProcessed_returns400() throws Exception {
        when(leaveService.updateLeaveStatus(eq(1L), anyString(), any(), anyString()))
                .thenThrow(new BadRequestException("Leave request already processed"));

        mockMvc.perform(patch("/api/leaves/1/action")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("status", "APPROVED"))))
                .andExpect(status().isBadRequest());
    }

    // ── DELETE /api/leaves/{leaveId} ─────────────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void deleteLeave_pendingLeave_returns204() throws Exception {
        doNothing().when(leaveService).deleteLeave(eq(1L), anyString());

        mockMvc.perform(delete("/api/leaves/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void deleteLeave_nonPendingLeave_returns400() throws Exception {
        doThrow(new BadRequestException("Only pending leaves can be deleted"))
                .when(leaveService).deleteLeave(eq(1L), anyString());

        mockMvc.perform(delete("/api/leaves/1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void deleteLeave_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Leave", 999L))
                .when(leaveService).deleteLeave(eq(999L), anyString());

        mockMvc.perform(delete("/api/leaves/999"))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/leaves/manager/{managerId}/pending ───────────────────────────

    @Test
    @WithMockUser(roles = "MANAGER")
    void getPendingForManager_asManager_returns200() throws Exception {
        when(leaveService.getPendingLeavesForManager(1L)).thenReturn(List.of(sampleLeave));

        mockMvc.perform(get("/api/leaves/manager/1/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getPendingForManager_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/leaves/manager/1/pending"))
                .andExpect(status().isForbidden());
    }
}
