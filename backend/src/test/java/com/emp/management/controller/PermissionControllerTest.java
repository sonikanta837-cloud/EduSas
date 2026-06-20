package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.entity.Role;
import com.emp.management.entity.User;
import com.emp.management.exception.GlobalExceptionHandler;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.UserRepository;
import com.emp.management.security.JwtAuthFilter;
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

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PermissionController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class PermissionControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private UserRepository userRepository;

    // ── GET /api/permissions ──────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllUserPermissions_asAdmin_returns200() throws Exception {
        User user = User.builder().id(1L).email("emp@company.com")
                .role(Role.EMPLOYEE).active(true).allowedModules("leave,timesheet").build();
        when(userRepository.findAll()).thenReturn(List.of(user));

        mockMvc.perform(get("/api/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("emp@company.com"))
                .andExpect(jsonPath("$[0].allowedModules").value("leave,timesheet"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllUserPermissions_inactiveUsersFiltered_returns200() throws Exception {
        User activeUser  = User.builder().id(1L).email("active@company.com")
                .role(Role.EMPLOYEE).active(true).build();
        User inactiveUser = User.builder().id(2L).email("inactive@company.com")
                .role(Role.EMPLOYEE).active(false).build();
        when(userRepository.findAll()).thenReturn(List.of(activeUser, inactiveUser));

        mockMvc.perform(get("/api/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("active@company.com"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllUserPermissions_nullAllowedModules_returnsEmptyString() throws Exception {
        User user = User.builder().id(1L).email("emp@company.com")
                .role(Role.EMPLOYEE).active(true).allowedModules(null).build();
        when(userRepository.findAll()).thenReturn(List.of(user));

        mockMvc.perform(get("/api/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].allowedModules").value(""));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void getAllUserPermissions_asManager_returns403() throws Exception {
        mockMvc.perform(get("/api/permissions"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getAllUserPermissions_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/permissions"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAllUserPermissions_unauthenticated_returns401or403() throws Exception {
        mockMvc.perform(get("/api/permissions"))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s != 401 && s != 403) throw new AssertionError("Expected 401 or 403, got: " + s);
                });
    }

    // ── PUT /api/permissions/{userId} ─────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void updatePermissions_asAdmin_returns200() throws Exception {
        User user = User.builder().id(1L).email("emp@company.com")
                .role(Role.EMPLOYEE).active(true).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        mockMvc.perform(put("/api/permissions/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"allowedModules\":\"leave,pip\"}"))
                .andExpect(status().isOk());

        verify(userRepository).save(argThat(u -> "leave,pip".equals(((User) u).getAllowedModules())));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updatePermissions_blankModules_clearsPermissions() throws Exception {
        User user = User.builder().id(1L).email("emp@company.com")
                .role(Role.EMPLOYEE).active(true).allowedModules("leave").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        mockMvc.perform(put("/api/permissions/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"allowedModules\":\"\"}"))
                .andExpect(status().isOk());

        // Blank modules should set allowedModules to null
        verify(userRepository).save(argThat(u -> ((User) u).getAllowedModules() == null));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updatePermissions_userNotFound_returns404() throws Exception {
        when(userRepository.findById(999L))
                .thenThrow(new ResourceNotFoundException("User not found"));

        mockMvc.perform(put("/api/permissions/999")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"allowedModules\":\"leave\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void updatePermissions_asEmployee_returns403() throws Exception {
        mockMvc.perform(put("/api/permissions/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"allowedModules\":\"leave\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HR")
    void updatePermissions_asHr_returns403() throws Exception {
        mockMvc.perform(put("/api/permissions/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"allowedModules\":\"leave\"}"))
                .andExpect(status().isForbidden());
    }
}
