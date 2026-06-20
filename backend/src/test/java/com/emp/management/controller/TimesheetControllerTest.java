package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.dto.AttendanceSessionDTO;
import com.emp.management.dto.TimesheetDTO;
import com.emp.management.dto.TimesheetEntryDTO;
import com.emp.management.dto.WorkReportDTO;
import com.emp.management.entity.EmployeeDetails;
import com.emp.management.entity.Role;
import com.emp.management.entity.User;
import com.emp.management.exception.GlobalExceptionHandler;
import com.emp.management.repository.EmployeeDetailsRepository;
import com.emp.management.security.JwtAuthFilter;
import com.emp.management.service.TimesheetEntryService;
import com.emp.management.service.TimesheetService;
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
    @MockBean private TimesheetService timesheetService;
    @MockBean private TimesheetEntryService timesheetEntryService;
    @MockBean private EmployeeDetailsRepository employeeDetailsRepository;

    private EmployeeDetails empDetails;
    private EmployeeDetails adminDetails;
    private TimesheetDTO sampleTimesheet;
    private AttendanceSessionDTO sampleSession;

    @BeforeEach
    void setUp() {
        User empUser = User.builder().id(2L).email("emp@company.com").role(Role.EMPLOYEE).active(true).build();
        empDetails = EmployeeDetails.builder().id(2L).user(empUser).firstName("John").lastName("Doe")
                .active(true).build();
        empUser.setEmployeeDetails(empDetails);

        User adminUser = User.builder().id(1L).email("admin@company.com").role(Role.ADMIN).active(true).build();
        adminDetails = EmployeeDetails.builder().id(1L).user(adminUser).firstName("Admin").lastName("User")
                .active(true).build();
        adminUser.setEmployeeDetails(adminDetails);

        sampleTimesheet = TimesheetDTO.builder()
                .id(10L)
                .employeeId(2L)
                .workDate(LocalDate.now())
                .workingHours(8.0)
                .build();

        sampleSession = AttendanceSessionDTO.builder()
                .id(1L)
                .employeeId(2L)
                .workDate(LocalDate.now())
                .loginTime(java.time.LocalTime.now().minusHours(4))
                .build();
    }

    // ── POST /api/timesheets/check-in/{id} ────────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void checkIn_asSelf_returns200() throws Exception {
        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(empDetails));
        doNothing().when(timesheetService).checkIn(2L);

        mockMvc.perform(post("/api/timesheets/check-in/2"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin@company.com", roles = "ADMIN")
    void checkIn_asAdmin_returns200() throws Exception {
        when(employeeDetailsRepository.findByUserEmail("admin@company.com")).thenReturn(Optional.of(adminDetails));
        doNothing().when(timesheetService).checkIn(2L);

        mockMvc.perform(post("/api/timesheets/check-in/2"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "other@company.com", roles = "EMPLOYEE")
    void checkIn_asOther_returns403() throws Exception {
        User otherUser = User.builder().id(3L).email("other@company.com").role(Role.EMPLOYEE).active(true).build();
        EmployeeDetails otherEmp = EmployeeDetails.builder().id(3L).user(otherUser).build();
        otherUser.setEmployeeDetails(otherEmp);

        when(employeeDetailsRepository.findByUserEmail("other@company.com")).thenReturn(Optional.of(otherEmp));

        mockMvc.perform(post("/api/timesheets/check-in/2"))
                .andExpect(status().isForbidden());
    }

    // ── POST /api/timesheets/check-out/{id} ───────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void checkOut_asSelf_returns200() throws Exception {
        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(empDetails));
        doNothing().when(timesheetService).checkOut(2L);

        mockMvc.perform(post("/api/timesheets/check-out/2"))
                .andExpect(status().isOk());
    }

    // ── GET /api/timesheets/sessions/today/{id} ───────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void getTodaySessions_asSelf_returns200() throws Exception {
        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(empDetails));
        when(timesheetService.getTodaySessions(2L)).thenReturn(List.of(sampleSession));

        mockMvc.perform(get("/api/timesheets/sessions/today/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(username = "admin@company.com", roles = "ADMIN")
    void getTodaySessions_asAdmin_returns200() throws Exception {
        when(employeeDetailsRepository.findByUserEmail("admin@company.com")).thenReturn(Optional.of(adminDetails));
        when(timesheetService.getTodaySessions(2L)).thenReturn(List.of(sampleSession));

        mockMvc.perform(get("/api/timesheets/sessions/today/2"))
                .andExpect(status().isOk());
    }

    // ── GET /api/timesheets/today/{id} ────────────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void getTodayTimesheet_asSelf_returns200() throws Exception {
        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(empDetails));
        when(timesheetService.getTodayTimesheet(2L)).thenReturn(sampleTimesheet);

        mockMvc.perform(get("/api/timesheets/today/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workingHours").value(8.0));
    }

    // ── GET /api/timesheets/attendance/{id} ───────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void getAttendance_asSelf_returns200() throws Exception {
        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(empDetails));
        when(timesheetService.getMyTimesheets(2L)).thenReturn(List.of(sampleTimesheet));

        mockMvc.perform(get("/api/timesheets/attendance/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ── GET /api/timesheets/attendance/{id}/range ─────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void getAttendanceByRange_asSelf_returns200() throws Exception {
        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(empDetails));
        when(timesheetService.getTimesheetsByDateRange(eq(2L), any(), any())).thenReturn(List.of(sampleTimesheet));

        mockMvc.perform(get("/api/timesheets/attendance/2/range")
                        .param("start", "2026-06-01")
                        .param("end", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ── GET /api/timesheets/date/{date} ──────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getByDate_asAdmin_returns200() throws Exception {
        when(timesheetService.getTimesheetsByDate(any())).thenReturn(List.of(sampleTimesheet));

        mockMvc.perform(get("/api/timesheets/date/2026-06-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getByDate_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/timesheets/date/2026-06-15"))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/timesheets/entries/employee/{id} ─────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void getEntries_asSelf_returns200() throws Exception {
        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(empDetails));
        TimesheetEntryDTO entry = TimesheetEntryDTO.builder()
                .id(1L).employeeId(2L).projectName("EmpSAS").taskName("Dev")
                .date(LocalDate.now()).hours(8.0).build();
        when(timesheetEntryService.getMonthlyEntries(2L, 2026, 6)).thenReturn(List.of(entry));

        mockMvc.perform(get("/api/timesheets/entries/employee/2")
                        .param("year", "2026")
                        .param("month", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ── POST /api/timesheets/entries ──────────────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void saveEntry_newEntry_returns201() throws Exception {
        TimesheetEntryDTO dto = TimesheetEntryDTO.builder()
                .employeeId(2L).projectName("EmpSAS").taskName("Dev")
                .date(LocalDate.now()).hours(8.0).build();
        TimesheetEntryDTO saved = TimesheetEntryDTO.builder()
                .id(1L).employeeId(2L).projectName("EmpSAS").taskName("Dev")
                .date(LocalDate.now()).hours(8.0).build();

        when(timesheetEntryService.saveEntry(any(), eq("emp@company.com"))).thenReturn(saved);

        mockMvc.perform(post("/api/timesheets/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void saveEntry_updateExisting_returns200() throws Exception {
        TimesheetEntryDTO dto = TimesheetEntryDTO.builder()
                .id(1L).employeeId(2L).projectName("EmpSAS").taskName("Dev")
                .date(LocalDate.now()).hours(8.0).build();
        when(timesheetEntryService.saveEntry(any(), eq("emp@company.com"))).thenReturn(dto);

        mockMvc.perform(post("/api/timesheets/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    // ── DELETE /api/timesheets/entries/{id} ───────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void deleteEntry_returns204() throws Exception {
        doNothing().when(timesheetEntryService).deleteEntry(eq(1L), eq("emp@company.com"));

        mockMvc.perform(delete("/api/timesheets/entries/1"))
                .andExpect(status().isNoContent());
    }

    // ── DELETE /api/timesheets/entries/project ────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void deleteProjectRows_returns204() throws Exception {
        doNothing().when(timesheetEntryService)
                .deleteProjectRows(anyLong(), anyString(), anyString(), anyString());

        mockMvc.perform(delete("/api/timesheets/entries/project")
                        .param("empId", "2")
                        .param("projectName", "EmpSAS")
                        .param("taskName", "Dev"))
                .andExpect(status().isNoContent());
    }

    // ── GET /api/timesheets/work-report ───────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getWorkReport_asAdmin_returns200() throws Exception {
        WorkReportDTO report = WorkReportDTO.builder()
                .employeeId(2L).employeeName("John Doe").hours(160.0).build();
        when(timesheetEntryService.getWorkReport(any(), any())).thenReturn(List.of(report));

        mockMvc.perform(get("/api/timesheets/work-report")
                        .param("start", "2026-06-01")
                        .param("end", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getWorkReport_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/timesheets/work-report")
                        .param("start", "2026-06-01")
                        .param("end", "2026-06-30"))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/timesheets/sessions/{id}/range ───────────────────────────────

    @Test
    @WithMockUser(username = "admin@company.com", roles = "ADMIN")
    void getSessionsByRange_asAdmin_returns200() throws Exception {
        when(employeeDetailsRepository.findByUserEmail("admin@company.com")).thenReturn(Optional.of(adminDetails));
        when(timesheetService.getSessionsByRange(anyLong(), any(), any())).thenReturn(List.of(sampleSession));

        mockMvc.perform(get("/api/timesheets/sessions/2/range")
                        .param("start", "2026-06-01")
                        .param("end", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
