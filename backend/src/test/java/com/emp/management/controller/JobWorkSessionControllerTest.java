package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.dto.JobSessionLoginRequest;
import com.emp.management.dto.JobWorkSessionDTO;
import com.emp.management.entity.EmployeeDetails;
import com.emp.management.entity.Role;
import com.emp.management.entity.User;
import com.emp.management.exception.BadRequestException;
import com.emp.management.exception.GlobalExceptionHandler;
import com.emp.management.repository.EmployeeDetailsRepository;
import com.emp.management.security.JwtAuthFilter;
import com.emp.management.service.JobWorkSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = JobWorkSessionController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class JobWorkSessionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private JobWorkSessionService jobWorkSessionService;
    @MockBean private EmployeeDetailsRepository employeeDetailsRepository;

    private EmployeeDetails selfEmployee(String email, long id, Role role) {
        User user = User.builder().id(id).email(email).role(role).active(true).build();
        return EmployeeDetails.builder().id(id).user(user).active(true).build();
    }

    private JobSessionLoginRequest loginRequest(Long employeeId) {
        return JobSessionLoginRequest.builder()
                .employeeId(employeeId).clientId(1L).jobId(1L).jobTypeId(1L).periodEndId(1L).build();
    }

    // ── POST /login ────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void login_self_returns201() throws Exception {
        when(employeeDetailsRepository.findByUserEmail("emp@company.com"))
                .thenReturn(Optional.of(selfEmployee("emp@company.com", 2L, Role.EMPLOYEE)));
        when(jobWorkSessionService.login(any())).thenReturn(JobWorkSessionDTO.builder().id(1L).build());

        mockMvc.perform(post("/api/job-sessions/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest(2L))))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void login_forAnotherEmployee_returns403() throws Exception {
        when(employeeDetailsRepository.findByUserEmail("emp@company.com"))
                .thenReturn(Optional.of(selfEmployee("emp@company.com", 2L, Role.EMPLOYEE)));

        mockMvc.perform(post("/api/job-sessions/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest(999L))))
                .andExpect(status().isForbidden());

        verify(jobWorkSessionService, never()).login(any());
    }

    @Test
    @WithMockUser(username = "admin@company.com", roles = "ADMIN")
    void login_adminActingForAnyEmployee_returns201() throws Exception {
        when(employeeDetailsRepository.findByUserEmail("admin@company.com"))
                .thenReturn(Optional.of(selfEmployee("admin@company.com", 1L, Role.ADMIN)));
        when(jobWorkSessionService.login(any())).thenReturn(JobWorkSessionDTO.builder().id(1L).build());

        mockMvc.perform(post("/api/job-sessions/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest(999L))))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "mgr@company.com", roles = "MANAGER")
    void login_managerActingForAnotherEmployee_returns403() throws Exception {
        // requireSelfOrAdmin only exempts ADMIN/DIRECTOR — MANAGER must still be self
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com"))
                .thenReturn(Optional.of(selfEmployee("mgr@company.com", 3L, Role.MANAGER)));

        mockMvc.perform(post("/api/job-sessions/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest(999L))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void login_missingClientId_returns400() throws Exception {
        when(employeeDetailsRepository.findByUserEmail("emp@company.com"))
                .thenReturn(Optional.of(selfEmployee("emp@company.com", 2L, Role.EMPLOYEE)));
        JobSessionLoginRequest req = JobSessionLoginRequest.builder().employeeId(2L).build();

        mockMvc.perform(post("/api/job-sessions/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void login_alreadyHasOpenSession_returns400FromService() throws Exception {
        when(employeeDetailsRepository.findByUserEmail("emp@company.com"))
                .thenReturn(Optional.of(selfEmployee("emp@company.com", 2L, Role.EMPLOYEE)));
        when(jobWorkSessionService.login(any()))
                .thenThrow(new BadRequestException("You already have an active job session — please log out before starting a new one"));

        mockMvc.perform(post("/api/job-sessions/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest(2L))))
                .andExpect(status().isBadRequest());
    }

    // ── POST /logout/{employeeId} ─────────────────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void logout_self_returns200() throws Exception {
        when(employeeDetailsRepository.findByUserEmail("emp@company.com"))
                .thenReturn(Optional.of(selfEmployee("emp@company.com", 2L, Role.EMPLOYEE)));
        when(jobWorkSessionService.logout(2L)).thenReturn(JobWorkSessionDTO.builder().id(1L).build());

        mockMvc.perform(post("/api/job-sessions/logout/2"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void logout_forAnotherEmployee_returns403() throws Exception {
        when(employeeDetailsRepository.findByUserEmail("emp@company.com"))
                .thenReturn(Optional.of(selfEmployee("emp@company.com", 2L, Role.EMPLOYEE)));

        mockMvc.perform(post("/api/job-sessions/logout/999"))
                .andExpect(status().isForbidden());
    }

    // ── POST /break/start, /break/end ─────────────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void startBreak_self_returns200() throws Exception {
        when(employeeDetailsRepository.findByUserEmail("emp@company.com"))
                .thenReturn(Optional.of(selfEmployee("emp@company.com", 2L, Role.EMPLOYEE)));
        when(jobWorkSessionService.startBreak(2L)).thenReturn(JobWorkSessionDTO.builder().id(1L).build());

        mockMvc.perform(post("/api/job-sessions/break/start/2"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void endBreak_noActiveBreak_returns400FromService() throws Exception {
        when(employeeDetailsRepository.findByUserEmail("emp@company.com"))
                .thenReturn(Optional.of(selfEmployee("emp@company.com", 2L, Role.EMPLOYEE)));
        when(jobWorkSessionService.endBreak(2L)).thenThrow(new BadRequestException("no active break"));

        mockMvc.perform(post("/api/job-sessions/break/end/2"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /today/{employeeId} — requireSelfOrPrivileged (broader than login/logout) ──

    @Test
    @WithMockUser(username = "mgr@company.com", roles = "MANAGER")
    void getToday_managerViewingSubordinate_returns200() throws Exception {
        // /today uses requireSelfOrPrivileged — MANAGER counts as privileged here,
        // unlike requireSelfOrAdmin used by /login and /logout
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com"))
                .thenReturn(Optional.of(selfEmployee("mgr@company.com", 3L, Role.MANAGER)));
        when(jobWorkSessionService.getToday(2L)).thenReturn(
                com.emp.management.dto.JobSessionTodayDTO.builder().totalMinutesToday(120).build());

        mockMvc.perform(get("/api/job-sessions/today/2"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void getToday_employeeViewingAnother_returns403() throws Exception {
        when(employeeDetailsRepository.findByUserEmail("emp@company.com"))
                .thenReturn(Optional.of(selfEmployee("emp@company.com", 2L, Role.EMPLOYEE)));

        mockMvc.perform(get("/api/job-sessions/today/999"))
                .andExpect(status().isForbidden());

        verify(jobWorkSessionService, never()).getToday(any());
    }

    // ── GET /range/{employeeId} ────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void getRange_self_returns200() throws Exception {
        when(employeeDetailsRepository.findByUserEmail("emp@company.com"))
                .thenReturn(Optional.of(selfEmployee("emp@company.com", 2L, Role.EMPLOYEE)));
        when(jobWorkSessionService.getRange(eq(2L), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/job-sessions/range/2")
                        .param("start", LocalDate.now().minusDays(7).toString())
                        .param("end", LocalDate.now().toString()))
                .andExpect(status().isOk());
    }

    // ── GET /work-report ──────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "MANAGER")
    void getWorkReport_asManager_returns200() throws Exception {
        when(jobWorkSessionService.getWorkReport(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/job-sessions/work-report")
                        .param("start", LocalDate.now().minusDays(7).toString())
                        .param("end", LocalDate.now().toString()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getWorkReport_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/job-sessions/work-report")
                        .param("start", LocalDate.now().minusDays(7).toString())
                        .param("end", LocalDate.now().toString()))
                .andExpect(status().isForbidden());
    }
}
