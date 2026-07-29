package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.dto.EmployeeDTO;
import com.emp.management.dto.RegisterRequest;
import com.emp.management.entity.Role;
import com.emp.management.exception.BadRequestException;
import com.emp.management.exception.GlobalExceptionHandler;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.security.JwtAuthFilter;
import com.emp.management.service.EmployeeService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = EmployeeController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class EmployeeControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private EmployeeService employeeService;

    private EmployeeDTO sampleDTO;

    @BeforeEach
    void setUp() {
        sampleDTO = EmployeeDTO.builder()
                .id(1L)
                .userId(10L)
                .email("alice@company.com")
                .firstName("Alice")
                .lastName("Smith")
                .employeeCode("10001")
                .role("EMPLOYEE")
                .active(true)
                .build();
    }

    // ── GET /api/employees ───────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllEmployees_asAdmin_returns200WithList() throws Exception {
        when(employeeService.getAllEmployees(anyString())).thenReturn(List.of(sampleDTO));

        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("alice@company.com"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getAllEmployees_asEmployee_returns200() throws Exception {
        when(employeeService.getAllEmployees(anyString())).thenReturn(List.of(sampleDTO));

        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isOk());
    }

    @Test
    void getAllEmployees_unauthenticated_returns401or403() throws Exception {
        mockMvc.perform(get("/api/employees"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assert status == 401 || status == 403;
                });
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllEmployees_withSearchParam_callsSearch() throws Exception {
        when(employeeService.searchEmployees(eq("Alice"), any())).thenReturn(List.of(sampleDTO));

        mockMvc.perform(get("/api/employees").param("search", "Alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].firstName").value("Alice"));

        verify(employeeService).searchEmployees(eq("Alice"), any());
        verify(employeeService, never()).getAllEmployees(anyString());
    }

    // ── GET /api/employees/{id} ──────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice@company.com", roles = "EMPLOYEE")
    void getEmployee_existingId_returns200() throws Exception {
        when(employeeService.getEmployeeById(1L, "alice@company.com")).thenReturn(sampleDTO);

        mockMvc.perform(get("/api/employees/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("Alice"));
    }

    @Test
    @WithMockUser(username = "admin@company.com", roles = "ADMIN")
    void getEmployee_nonExistingId_returns404() throws Exception {
        when(employeeService.getEmployeeById(eq(99L), anyString()))
                .thenThrow(new ResourceNotFoundException("Employee", 99L));

        mockMvc.perform(get("/api/employees/99"))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/employees/ex ────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getExEmployees_asAdmin_returns200() throws Exception {
        EmployeeDTO inactiveDTO = EmployeeDTO.builder().id(2L).active(false).build();
        when(employeeService.getExEmployees()).thenReturn(List.of(inactiveDTO));

        mockMvc.perform(get("/api/employees/ex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getExEmployees_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/employees/ex"))
                .andExpect(status().isForbidden());
    }

    // ── POST /api/employees ──────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin@company.com", roles = "ADMIN")
    void createEmployee_asAdmin_returns200() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("new@company.com");
        req.setPassword("Password1");
        req.setFirstName("New");
        req.setLastName("Hire");
        req.setRole(Role.EMPLOYEE);

        when(employeeService.createEmployee(any(), eq("admin@company.com"))).thenReturn(sampleDTO);

        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value("Alice"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void createEmployee_asEmployee_returns403() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("new@company.com");
        req.setPassword("Password1");
        req.setFirstName("New");
        req.setLastName("Hire");
        req.setRole(Role.EMPLOYEE);

        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@company.com", roles = "ADMIN")
    void createEmployee_duplicateEmail_returns400() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("existing@company.com");
        req.setPassword("Password1");
        req.setFirstName("Dup");
        req.setLastName("User");
        req.setRole(Role.EMPLOYEE);

        when(employeeService.createEmployee(any(), anyString()))
                .thenThrow(new BadRequestException("Email already registered"));

        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ── PUT /api/employees/{id} ──────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin@company.com", roles = "ADMIN")
    void updateEmployee_asAdmin_returns200() throws Exception {
        EmployeeDTO updateDTO = EmployeeDTO.builder().firstName("Updated").lastName("Name").build();
        when(employeeService.updateEmployee(eq(1L), any(), eq("admin@company.com"))).thenReturn(sampleDTO);

        mockMvc.perform(put("/api/employees/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDTO)))
                .andExpect(status().isOk());
    }

    // ── PATCH /api/employees/{id}/toggle-status ──────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void toggleStatus_asAdmin_returns200() throws Exception {
        doNothing().when(employeeService).toggleEmployeeStatus(1L);

        mockMvc.perform(patch("/api/employees/1/toggle-status"))
                .andExpect(status().isOk())
                .andExpect(content().string("Status updated"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void toggleStatus_asManager_returns403() throws Exception {
        mockMvc.perform(patch("/api/employees/1/toggle-status"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void toggleStatus_nonExistingId_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Employee", 99L))
                .when(employeeService).toggleEmployeeStatus(99L);

        mockMvc.perform(patch("/api/employees/99/toggle-status"))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /api/employees/{id} ───────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteEmployee_asAdmin_returns200() throws Exception {
        doNothing().when(employeeService).deleteEmployee(1L);

        mockMvc.perform(delete("/api/employees/1"))
                .andExpect(status().isOk())
                .andExpect(content().string("Employee deactivated"));
    }

    @Test
    @WithMockUser(roles = "HR")
    void deleteEmployee_asHR_returns403() throws Exception {
        mockMvc.perform(delete("/api/employees/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteEmployee_nonExistingId_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Employee", 99L))
                .when(employeeService).deleteEmployee(99L);

        mockMvc.perform(delete("/api/employees/99"))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/employees/org-chart ─────────────────────────────────────────

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getOrgChart_authenticated_returns200() throws Exception {
        when(employeeService.getOrgChart()).thenReturn(List.of(sampleDTO));

        mockMvc.perform(get("/api/employees/org-chart"))
                .andExpect(status().isOk());
    }

    // ── PUT /api/employees/{id} — validation ─────────────────────────────────

    @Test
    @WithMockUser(username = "admin@company.com", roles = "ADMIN")
    void updateEmployee_negativeSalary_returns400() throws Exception {
        EmployeeDTO dto = EmployeeDTO.builder()
                .firstName("Alice").lastName("Smith").salary(new java.math.BigDecimal("-500")).build();

        mockMvc.perform(put("/api/employees/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());

        verify(employeeService, never()).updateEmployee(any(), any(), any());
    }

    @Test
    @WithMockUser(username = "admin@company.com", roles = "ADMIN")
    void updateEmployee_salaryExactlyZero_isAccepted() throws Exception {
        EmployeeDTO dto = EmployeeDTO.builder()
                .firstName("Alice").lastName("Smith").salary(java.math.BigDecimal.ZERO).build();
        when(employeeService.updateEmployee(eq(1L), any(), eq("admin@company.com"))).thenReturn(sampleDTO);

        mockMvc.perform(put("/api/employees/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin@company.com", roles = "ADMIN")
    void updateEmployee_invalidPhonePattern_returns400() throws Exception {
        EmployeeDTO dto = EmployeeDTO.builder()
                .firstName("Alice").lastName("Smith").phone("123").build(); // below 7-digit minimum

        mockMvc.perform(put("/api/employees/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin@company.com", roles = "ADMIN")
    void updateEmployee_blankFirstName_returns400() throws Exception {
        EmployeeDTO dto = EmployeeDTO.builder().firstName("").lastName("Smith").build();

        mockMvc.perform(put("/api/employees/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin@company.com", roles = "ADMIN")
    void updateEmployee_invalidEmailFormat_returns400() throws Exception {
        EmployeeDTO dto = EmployeeDTO.builder()
                .firstName("Alice").lastName("Smith").email("not-an-email").build();

        mockMvc.perform(put("/api/employees/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    // ── PATCH /api/employees/{id}/clear-manager ──────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void clearManager_asAdmin_returns200() throws Exception {
        doNothing().when(employeeService).clearManager(1L);

        mockMvc.perform(patch("/api/employees/1/clear-manager"))
                .andExpect(status().isOk())
                .andExpect(content().string("Manager cleared"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void clearManager_asManager_returns403() throws Exception {
        mockMvc.perform(patch("/api/employees/1/clear-manager"))
                .andExpect(status().isForbidden());

        verify(employeeService, never()).clearManager(any());
    }

    // ── PATCH /api/employees/{id}/assign-hr ──────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void assignHr_asAdmin_returns200() throws Exception {
        when(employeeService.assignHr(1L, 5L)).thenReturn(sampleDTO);

        mockMvc.perform(patch("/api/employees/1/assign-hr").param("hrId", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@company.com"));
    }

    @Test
    @WithMockUser(roles = "HR")
    void assignHr_asHR_returns403() throws Exception {
        // Assigning HR is narrower than most HR-utility writes — HR itself cannot reassign
        mockMvc.perform(patch("/api/employees/1/assign-hr").param("hrId", "5"))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/employees/hr-users ───────────────────────────────────────────

    @Test
    @WithMockUser(roles = "HR")
    void getHrUsers_asHR_returns200() throws Exception {
        when(employeeService.getHrEmployees()).thenReturn(List.of(sampleDTO));

        mockMvc.perform(get("/api/employees/hr-users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void getHrUsers_asManager_returns403() throws Exception {
        mockMvc.perform(get("/api/employees/hr-users"))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/employees/manager/{managerId}/team ──────────────────────────

    @Test
    @WithMockUser(roles = "MANAGER")
    void getTeam_returns200WithDirectReports() throws Exception {
        when(employeeService.getEmployeesByManager(1L)).thenReturn(List.of(sampleDTO));

        mockMvc.perform(get("/api/employees/manager/1/team"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ── GET /api/employees/locations ──────────────────────────────────────────

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getLocations_anyAuthenticatedRole_returns200() throws Exception {
        when(employeeService.getDistinctLocations()).thenReturn(List.of("Pune", "Mumbai"));

        mockMvc.perform(get("/api/employees/locations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
