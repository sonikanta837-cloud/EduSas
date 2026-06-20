package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.dto.PipNotificationDTO;
import com.emp.management.exception.GlobalExceptionHandler;
import com.emp.management.security.JwtAuthFilter;
import com.emp.management.service.PipNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PipNotificationController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class PipNotificationControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private PipNotificationService service;

    // ── GET /api/pip-notifications/unread ─────────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void getUnread_asEmployee_returns200WithList() throws Exception {
        PipNotificationDTO dto = PipNotificationDTO.builder()
                .id(1L).message("Your PIP has been updated").read(false).build();
        when(service.getUnread("emp@company.com")).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/pip-notifications/unread"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].message").value("Your PIP has been updated"));
    }

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void getUnread_emptyList_returns200() throws Exception {
        when(service.getUnread("emp@company.com")).thenReturn(List.of());

        mockMvc.perform(get("/api/pip-notifications/unread"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getUnread_unauthenticated_returns401or403() throws Exception {
        mockMvc.perform(get("/api/pip-notifications/unread"))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s != 401 && s != 403) throw new AssertionError("Expected 401 or 403, got: " + s);
                });
    }

    // ── GET /api/pip-notifications/unread-count ───────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void getUnreadCount_returns200WithCount() throws Exception {
        when(service.getUnreadCount("emp@company.com")).thenReturn(5L);

        mockMvc.perform(get("/api/pip-notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(5));
    }

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void getUnreadCount_zeroCount_returns200() throws Exception {
        when(service.getUnreadCount("emp@company.com")).thenReturn(0L);

        mockMvc.perform(get("/api/pip-notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    // ── POST /api/pip-notifications/{id}/read ────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void markAsRead_returns204() throws Exception {
        doNothing().when(service).markAsRead(1L, "emp@company.com");

        mockMvc.perform(post("/api/pip-notifications/1/read"))
                .andExpect(status().isNoContent());
    }

    // ── POST /api/pip-notifications/read-all ─────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void markAllRead_returns204() throws Exception {
        doNothing().when(service).markAllRead("emp@company.com");

        mockMvc.perform(post("/api/pip-notifications/read-all"))
                .andExpect(status().isNoContent());
    }
}
