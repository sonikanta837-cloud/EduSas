package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.entity.EmployeeDetails;
import com.emp.management.entity.Role;
import com.emp.management.entity.User;
import com.emp.management.exception.GlobalExceptionHandler;
import com.emp.management.repository.UserRepository;
import com.emp.management.security.JwtAuthFilter;
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
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PermissionController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class PermissionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private UserRepository userRepository;

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllUserPermissions_asAdmin_returns200_excludesInactiveUsers() throws Exception {
        User active = User.builder().id(1L).email("active@company.com").role(Role.EMPLOYEE).active(true).build();
        EmployeeDetails emp = EmployeeDetails.builder().id(1L).firstName("Active").lastName("User").build();
        active.setEmployeeDetails(emp);
        User inactive = User.builder().id(2L).email("inactive@company.com").role(Role.EMPLOYEE).active(false).build();

        when(userRepository.findAll()).thenReturn(List.of(active, inactive));

        mockMvc.perform(get("/api/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("active@company.com"));
    }

    @Test
    @WithMockUser(roles = "HR")
    void getAllUserPermissions_asHR_returns403() throws Exception {
        mockMvc.perform(get("/api/permissions"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updatePermissions_setsAllowedModules() throws Exception {
        User user = User.builder().id(5L).email("u@company.com").role(Role.EMPLOYEE).active(true).build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        mockMvc.perform(put("/api/permissions/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("allowedModules", "[\"/dashboard\",\"/employees\"]"))))
                .andExpect(status().isOk());

        verify(userRepository).save(argThat(u -> "[\"/dashboard\",\"/employees\"]".equals(u.getAllowedModules())));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updatePermissions_blankModules_clearsOverrideToRoleDefaults() throws Exception {
        User user = User.builder().id(5L).email("u@company.com").role(Role.EMPLOYEE).active(true)
                .allowedModules("[\"/old\"]").build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        mockMvc.perform(put("/api/permissions/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("allowedModules", ""))))
                .andExpect(status().isOk());

        verify(userRepository).save(argThat(u -> u.getAllowedModules() == null));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updatePermissions_userNotFound_returns404() throws Exception {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/permissions/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("allowedModules", "x"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void updatePermissions_asManager_returns403() throws Exception {
        mockMvc.perform(put("/api/permissions/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("allowedModules", "x"))))
                .andExpect(status().isForbidden());

        verify(userRepository, never()).findById(any());
    }
}
