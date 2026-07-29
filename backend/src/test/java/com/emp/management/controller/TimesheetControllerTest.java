package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.dto.TimesheetEntryDTO;
import com.emp.management.entity.EmployeeDetails;
import com.emp.management.entity.Role;
import com.emp.management.entity.User;
import com.emp.management.exception.GlobalExceptionHandler;
import com.emp.management.repository.EmployeeDetailsRepository;
import com.emp.management.security.JwtAuthFilter;
import com.emp.management.service.TimesheetEntryService;
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

@WebMvcTest(controllers = TimesheetController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class TimesheetControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private TimesheetEntryService timesheetEntryService;
    @MockBean private EmployeeDetailsRepository employeeDetailsRepository;

    private EmployeeDetails selfEmployee(String email, long id, Role role) {
        User user = User.builder().id(id).email(email).role(role).active(true).build();
        return EmployeeDetails.builder().id(id).user(user).active(true).build();
    }

    private TimesheetEntryDTO validEntry() {
        return TimesheetEntryDTO.builder()
                .date(LocalDate.now()).projectName("Alpha").hours(8.0).build();
    }

    // ── GET /entries/employee/{empId} ────────────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void getEntries_self_returns200() throws Exception {
        when(employeeDetailsRepository.findByUserEmail("emp@company.com"))
                .thenReturn(Optional.of(selfEmployee("emp@company.com", 2L, Role.EMPLOYEE)));
        when(timesheetEntryService.getMonthlyEntries(2L, 2026, 7)).thenReturn(List.of(validEntry()));

        mockMvc.perform(get("/api/timesheets/entries/employee/2").param("year", "2026").param("month", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void getEntries_forAnotherEmployee_returns403() throws Exception {
        when(employeeDetailsRepository.findByUserEmail("emp@company.com"))
                .thenReturn(Optional.of(selfEmployee("emp@company.com", 2L, Role.EMPLOYEE)));

        mockMvc.perform(get("/api/timesheets/entries/employee/999").param("year", "2026").param("month", "7"))
                .andExpect(status().isForbidden());

        verify(timesheetEntryService, never()).getMonthlyEntries(any(), anyInt(), anyInt());
    }

    @Test
    @WithMockUser(username = "mgr@company.com", roles = "MANAGER")
    void getEntries_managerViewingSubordinate_returns200() throws Exception {
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com"))
                .thenReturn(Optional.of(selfEmployee("mgr@company.com", 3L, Role.MANAGER)));
        when(timesheetEntryService.getMonthlyEntries(2L, 2026, 7)).thenReturn(List.of());

        mockMvc.perform(get("/api/timesheets/entries/employee/2").param("year", "2026").param("month", "7"))
                .andExpect(status().isOk());
    }

    // ── POST /entries ──────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void saveEntry_newEntry_returns201() throws Exception {
        when(timesheetEntryService.saveEntry(any(), eq("emp@company.com"))).thenReturn(validEntry());

        mockMvc.perform(post("/api/timesheets/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validEntry())))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void saveEntry_existingEntryWithId_returns200NotCreated() throws Exception {
        TimesheetEntryDTO existing = validEntry();
        existing.setId(5L);
        when(timesheetEntryService.saveEntry(any(), eq("emp@company.com"))).thenReturn(existing);

        mockMvc.perform(post("/api/timesheets/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(existing)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void saveEntry_hoursExactlyZero_returns400() throws Exception {
        TimesheetEntryDTO dto = TimesheetEntryDTO.builder().date(LocalDate.now()).projectName("Alpha").hours(0.0).build();

        mockMvc.perform(post("/api/timesheets/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void saveEntry_hoursExactly24_isAccepted() throws Exception {
        TimesheetEntryDTO dto = TimesheetEntryDTO.builder().date(LocalDate.now()).projectName("Alpha").hours(24.0).build();
        when(timesheetEntryService.saveEntry(any(), anyString())).thenReturn(dto);

        mockMvc.perform(post("/api/timesheets/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void saveEntry_hoursAbove24_returns400() throws Exception {
        TimesheetEntryDTO dto = TimesheetEntryDTO.builder().date(LocalDate.now()).projectName("Alpha").hours(24.01).build();

        mockMvc.perform(post("/api/timesheets/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void saveEntry_blankProjectName_returns400() throws Exception {
        TimesheetEntryDTO dto = TimesheetEntryDTO.builder().date(LocalDate.now()).projectName("").hours(8.0).build();

        mockMvc.perform(post("/api/timesheets/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void saveEntry_missingDate_returns400() throws Exception {
        TimesheetEntryDTO dto = TimesheetEntryDTO.builder().projectName("Alpha").hours(8.0).build();

        mockMvc.perform(post("/api/timesheets/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    // ── DELETE /entries/{id} ───────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void deleteEntry_returns204() throws Exception {
        doNothing().when(timesheetEntryService).deleteEntry(5L, "emp@company.com");

        mockMvc.perform(delete("/api/timesheets/entries/5"))
                .andExpect(status().isNoContent());
    }

    // ── DELETE /entries/project ─────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void deleteProjectRows_returns204() throws Exception {
        doNothing().when(timesheetEntryService).deleteProjectRows(2L, "Alpha", "Design", "emp@company.com");

        mockMvc.perform(delete("/api/timesheets/entries/project")
                        .param("empId", "2").param("projectName", "Alpha").param("taskName", "Design"))
                .andExpect(status().isNoContent());
    }

    // ── GET /work-report ───────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "HR")
    void getWorkReport_asHR_returns200() throws Exception {
        when(timesheetEntryService.getWorkReport(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/timesheets/work-report")
                        .param("start", LocalDate.now().minusDays(7).toString())
                        .param("end", LocalDate.now().toString()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getWorkReport_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/timesheets/work-report")
                        .param("start", LocalDate.now().minusDays(7).toString())
                        .param("end", LocalDate.now().toString()))
                .andExpect(status().isForbidden());
    }
}
