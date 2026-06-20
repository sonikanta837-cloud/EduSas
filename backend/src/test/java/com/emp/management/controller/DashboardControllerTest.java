package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.dto.DashboardDTO;
import com.emp.management.exception.GlobalExceptionHandler;
import com.emp.management.security.JwtAuthFilter;
import com.emp.management.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = DashboardController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class DashboardControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private DashboardService dashboardService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void getStats_asAdmin_returns200() throws Exception {
        DashboardDTO dto = DashboardDTO.builder()
                .totalEmployees(50L)
                .activeEmployees(45L)
                .pendingLeaves(3L)
                .build();
        when(dashboardService.getDashboardStats()).thenReturn(dto);

        mockMvc.perform(get("/api/dashboard/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEmployees").value(50));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getStats_asEmployee_returns200() throws Exception {
        DashboardDTO dto = DashboardDTO.builder().totalEmployees(50L).build();
        when(dashboardService.getDashboardStats()).thenReturn(dto);

        mockMvc.perform(get("/api/dashboard/stats"))
                .andExpect(status().isOk());
    }

    @Test
    void getStats_unauthenticated_returns401or403() throws Exception {
        mockMvc.perform(get("/api/dashboard/stats"))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s != 401 && s != 403) throw new AssertionError("Expected 401 or 403, got: " + s);
                });
    }
}
