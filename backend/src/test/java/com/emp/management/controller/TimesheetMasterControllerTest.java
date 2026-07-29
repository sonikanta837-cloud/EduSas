package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.dto.MasterValueCreateRequest;
import com.emp.management.dto.MasterValueUpdateRequest;
import com.emp.management.dto.TimesheetMasterValueDTO;
import com.emp.management.entity.EmployeeDetails;
import com.emp.management.exception.GlobalExceptionHandler;
import com.emp.management.repository.EmployeeDetailsRepository;
import com.emp.management.security.JwtAuthFilter;
import com.emp.management.service.TimesheetMasterValueService;
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

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = TimesheetMasterController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class TimesheetMasterControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private TimesheetMasterValueService masterValueService;
    @MockBean private EmployeeDetailsRepository employeeDetailsRepository;

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void list_anyAuthenticatedRole_returns200() throws Exception {
        when(masterValueService.list(eq(com.emp.management.entity.MasterDataType.CLIENT), eq(true)))
                .thenReturn(List.of(TimesheetMasterValueDTO.builder().id(1L).value("Acme Corp").build()));

        mockMvc.perform(get("/api/timesheet-master/CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void add_anyRoleCanAddMasterValue_returns201() throws Exception {
        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.empty());
        MasterValueCreateRequest req = MasterValueCreateRequest.builder().value("Acme Corp").build();
        when(masterValueService.add(eq(com.emp.management.entity.MasterDataType.CLIENT), any(), anyString()))
                .thenReturn(TimesheetMasterValueDTO.builder().id(1L).value("Acme Corp").build());

        mockMvc.perform(post("/api/timesheet-master/CLIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.value").value("Acme Corp"));
    }

    @Test
    void add_unauthenticated_returns401or403() throws Exception {
        MasterValueCreateRequest req = MasterValueCreateRequest.builder().value("Acme Corp").build();

        mockMvc.perform(post("/api/timesheet-master/CLIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assert status == 401 || status == 403;
                });
    }

    @Test
    @WithMockUser(roles = "HR")
    void update_asHR_returns200() throws Exception {
        MasterValueUpdateRequest req = MasterValueUpdateRequest.builder().value("Updated Corp").active(true).build();
        when(masterValueService.update(eq(1L), any()))
                .thenReturn(TimesheetMasterValueDTO.builder().id(1L).value("Updated Corp").build());

        mockMvc.perform(put("/api/timesheet-master/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("Updated Corp"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void update_asManager_returns403() throws Exception {
        // Update is narrower than add — any role can add a master value, but only
        // ADMIN/DIRECTOR/HR can update an existing one
        MasterValueUpdateRequest req = MasterValueUpdateRequest.builder().value("x").build();

        mockMvc.perform(put("/api/timesheet-master/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        verify(masterValueService, never()).update(any(), any());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void update_asEmployee_returns403() throws Exception {
        MasterValueUpdateRequest req = MasterValueUpdateRequest.builder().value("x").build();

        mockMvc.perform(put("/api/timesheet-master/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }
}
