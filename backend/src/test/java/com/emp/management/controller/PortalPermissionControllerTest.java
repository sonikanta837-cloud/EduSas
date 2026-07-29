package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.entity.Role;
import com.emp.management.entity.User;
import com.emp.management.exception.GlobalExceptionHandler;
import com.emp.management.repository.UserRepository;
import com.emp.management.security.JwtAuthFilter;
import com.emp.management.service.PortalPermissionService;
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
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PortalPermissionController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class PortalPermissionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private PortalPermissionService portalPermissionService;
    @MockBean private UserRepository userRepository;

    private User employeeUser;

    @BeforeEach
    void setUp() {
        employeeUser = User.builder().id(2L).email("emp@company.com").role(Role.EMPLOYEE).active(true).build();
    }

    // ── GET /api/portal-permissions/me ────────────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void getMine_returnsEffectivePortalList() throws Exception {
        when(userRepository.findByEmail("emp@company.com")).thenReturn(Optional.of(employeeUser));
        when(portalPermissionService.getEffectiveAllowedPortals(employeeUser)).thenReturn(List.of("hr", "training"));

        mockMvc.perform(get("/api/portal-permissions/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ── GET /api/portal-permissions/check/{portalId} ─────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void check_allowedPortal_returns200() throws Exception {
        when(userRepository.findByEmail("emp@company.com")).thenReturn(Optional.of(employeeUser));
        when(portalPermissionService.isPortalAllowedForUser(employeeUser, "hr")).thenReturn(true);

        mockMvc.perform(get("/api/portal-permissions/check/hr"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void check_disallowedPortal_returns403() throws Exception {
        when(userRepository.findByEmail("emp@company.com")).thenReturn(Optional.of(employeeUser));
        when(portalPermissionService.isPortalAllowedForUser(employeeUser, "tandem")).thenReturn(false);

        mockMvc.perform(get("/api/portal-permissions/check/tandem"))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/portal-permissions/roles ─────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllRolePermissions_asAdmin_returns200() throws Exception {
        when(portalPermissionService.getAllRolePermissions()).thenReturn(Map.of("EMPLOYEE", List.of("hr")));

        mockMvc.perform(get("/api/portal-permissions/roles"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "HR")
    void getAllRolePermissions_asHR_returns403() throws Exception {
        mockMvc.perform(get("/api/portal-permissions/roles"))
                .andExpect(status().isForbidden());
    }

    // ── PUT /api/portal-permissions/roles/{role} ─────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateRolePermissions_asAdmin_returns200() throws Exception {
        doNothing().when(portalPermissionService).updateRolePermissions(eq(Role.MANAGER), anyList());

        mockMvc.perform(put("/api/portal-permissions/roles/MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("portalIds", List.of("hr", "training")))))
                .andExpect(status().isOk());

        verify(portalPermissionService).updateRolePermissions(Role.MANAGER, List.of("hr", "training"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void updateRolePermissions_asManager_returns403() throws Exception {
        mockMvc.perform(put("/api/portal-permissions/roles/MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("portalIds", List.of("hr")))))
                .andExpect(status().isForbidden());

        verify(portalPermissionService, never()).updateRolePermissions(any(), any());
    }
}
