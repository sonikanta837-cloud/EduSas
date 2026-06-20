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

    // Valid RegisterRequest for controller tests (min 8-char password)
    private RegisterRequest validRequest(String email) {
        RegisterRequest req = new RegisterRequest();
        req.setEmail(email);
        req.setPassword("ValidPass1");
        req.setFirstName("New");
        req.setLastName("Hire");
        req.setRole(Role.EMPLOYEE);
        return req;
    }

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
    @WithMockUser(username = "admin@company.com", roles = "ADMIN")
    void getAllEmployees_asAdmin_returns200WithList() throws Exception {
        when(employeeService.getAllEmployees(anyString())).thenReturn(List.of(sampleDTO));

        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("alice@company.com"));
    }

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
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
    @WithMockUser(username = "admin@company.com", roles = "ADMIN")
    void getAllEmployees_withSearchParam_callsSearch() throws Exception {
        when(employeeService.searchEmployees(eq("Alice"), anyString())).thenReturn(List.of(sampleDTO));

        mockMvc.perform(get("/api/employees").param("search", "Alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].firstName").value("Alice"));

        verify(employeeService).searchEmployees(eq("Alice"), anyString());
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
    void createEmployee_asAdmin_returns201() throws Exception {
        when(employeeService.createEmployee(any(), eq("admin@company.com"))).thenReturn(sampleDTO);

        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest("new@company.com"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value("Alice"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void createEmployee_asEmployee_returns403() throws Exception {
        // Employee role should be rejected at auth level before validation
        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest("new@company.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@company.com", roles = "ADMIN")
    void createEmployee_duplicateEmail_returns400() throws Exception {
        when(employeeService.createEmployee(any(), anyString()))
                .thenThrow(new BadRequestException("Email already registered"));

        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest("existing@company.com"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin@company.com", roles = "ADMIN")
    void createEmployee_invalidEmail_returns400() throws Exception {
        RegisterRequest badReq = validRequest("not-an-email");
        badReq.setEmail("not-an-email");

        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badReq)))
                .andExpect(status().isBadRequest());
    }

    // ── PUT /api/employees/{id} ──────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin@company.com", roles = "ADMIN")
    void updateEmployee_asAdmin_returns200() throws Exception {
        when(employeeService.updateEmployee(eq(1L), any(), eq("admin@company.com"))).thenReturn(sampleDTO);

        mockMvc.perform(put("/api/employees/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@company.com"));
    }

    @Test
    @WithMockUser(username = "admin@company.com", roles = "ADMIN")
    void updateEmployee_nonExisting_returns404() throws Exception {
        when(employeeService.updateEmployee(eq(99L), any(), anyString()))
                .thenThrow(new ResourceNotFoundException("Employee", 99L));

        mockMvc.perform(put("/api/employees/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleDTO)))
                .andExpect(status().isNotFound());
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
    @WithMockUser(roles = "EMPLOYEE")
    void toggleStatus_asEmployee_returns403() throws Exception {
        mockMvc.perform(patch("/api/employees/1/toggle-status"))
                .andExpect(status().isForbidden());
    }

    // ── PATCH /api/employees/{id}/clear-manager ──────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void clearManager_asAdmin_returns200() throws Exception {
        doNothing().when(employeeService).clearManager(1L);

        mockMvc.perform(patch("/api/employees/1/clear-manager"))
                .andExpect(status().isOk());
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
    @WithMockUser(roles = "EMPLOYEE")
    void deleteEmployee_asEmployee_returns403() throws Exception {
        mockMvc.perform(delete("/api/employees/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteEmployee_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Employee", 999L))
                .when(employeeService).deleteEmployee(999L);

        mockMvc.perform(delete("/api/employees/999"))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/employees/org-chart ─────────────────────────────────────────

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getOrgChart_authenticated_returns200() throws Exception {
        when(employeeService.getOrgChart()).thenReturn(List.of(sampleDTO));

        mockMvc.perform(get("/api/employees/org-chart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ── GET /api/employees/locations ──────────────────────────────────────────

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getLocations_authenticated_returns200() throws Exception {
        when(employeeService.getDistinctLocations()).thenReturn(List.of("Mumbai", "Pune", "Bangalore"));

        mockMvc.perform(get("/api/employees/locations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }
}
