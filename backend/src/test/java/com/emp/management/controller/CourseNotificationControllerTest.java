package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.dto.CourseNotificationDTO;
import com.emp.management.exception.GlobalExceptionHandler;
import com.emp.management.security.JwtAuthFilter;
import com.emp.management.service.CourseNotificationService;
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

@WebMvcTest(controllers = CourseNotificationController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class CourseNotificationControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private CourseNotificationService service;

    // ── GET /api/course-notifications/unread ──────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void getUnread_asEmployee_returns200() throws Exception {
        CourseNotificationDTO dto = CourseNotificationDTO.builder()
                .id(1L).courseTitle("Java Fundamentals").read(false).build();
        when(service.getUnread("emp@company.com")).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/course-notifications/unread"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].courseTitle").value("Java Fundamentals"));
    }

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void getUnread_emptyList_returns200() throws Exception {
        when(service.getUnread("emp@company.com")).thenReturn(List.of());

        mockMvc.perform(get("/api/course-notifications/unread"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getUnread_unauthenticated_returns401or403() throws Exception {
        mockMvc.perform(get("/api/course-notifications/unread"))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s != 401 && s != 403) throw new AssertionError("Expected 401 or 403, got: " + s);
                });
    }

    // ── GET /api/course-notifications/unread-count ────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void getUnreadCount_returns200WithCount() throws Exception {
        when(service.getUnreadCount("emp@company.com")).thenReturn(3L);

        mockMvc.perform(get("/api/course-notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));
    }

    // ── POST /api/course-notifications/{id}/read ──────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void markAsRead_returns204() throws Exception {
        doNothing().when(service).markAsRead(1L, "emp@company.com");

        mockMvc.perform(post("/api/course-notifications/1/read"))
                .andExpect(status().isNoContent());
    }

    // ── POST /api/course-notifications/read-all ───────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void markAllRead_returns204() throws Exception {
        doNothing().when(service).markAllRead("emp@company.com");

        mockMvc.perform(post("/api/course-notifications/read-all"))
                .andExpect(status().isNoContent());
    }
}
