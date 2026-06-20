package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.dto.AnnouncementDTO;
import com.emp.management.dto.AnnouncementViewersDTO;
import com.emp.management.exception.GlobalExceptionHandler;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.security.JwtAuthFilter;
import com.emp.management.service.AnnouncementService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AnnouncementController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class AnnouncementControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private AnnouncementService service;

    private AnnouncementDTO sampleAnnouncement;

    @BeforeEach
    void setUp() {
        sampleAnnouncement = AnnouncementDTO.builder()
                .id(1L)
                .title("Company Picnic")
                .body("Join us for the annual picnic on July 4th!")
                .authorName("admin@company.com")
                .build();
    }

    // ── GET /api/announcements ────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void getAll_authenticated_returns200() throws Exception {
        when(service.getAll("emp@company.com")).thenReturn(List.of(sampleAnnouncement));

        mockMvc.perform(get("/api/announcements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Company Picnic"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getAll_emptyList_returns200() throws Exception {
        when(service.getAll(anyString())).thenReturn(List.of());

        mockMvc.perform(get("/api/announcements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── GET /api/announcements/unread ─────────────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void getUnread_returns200() throws Exception {
        when(service.getUnread("emp@company.com")).thenReturn(List.of(sampleAnnouncement));

        mockMvc.perform(get("/api/announcements/unread"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ── GET /api/announcements/unread-count ───────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void getUnreadCount_returns200() throws Exception {
        when(service.getUnreadCount("emp@company.com")).thenReturn(3L);

        mockMvc.perform(get("/api/announcements/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));
    }

    // ── POST /api/announcements ───────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin@company.com", roles = "ADMIN")
    void create_asAdmin_returns200() throws Exception {
        when(service.create(any())).thenReturn(sampleAnnouncement);

        mockMvc.perform(post("/api/announcements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleAnnouncement)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Company Picnic"));
    }

    @Test
    @WithMockUser(username = "hr@company.com", roles = "HR")
    void create_asHR_returns200() throws Exception {
        when(service.create(any())).thenReturn(sampleAnnouncement);

        mockMvc.perform(post("/api/announcements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleAnnouncement)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void create_asEmployee_returns403() throws Exception {
        mockMvc.perform(post("/api/announcements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleAnnouncement)))
                .andExpect(status().isForbidden());
    }

    // ── PUT /api/announcements/{id} ───────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_asAdmin_returns200() throws Exception {
        when(service.update(eq(1L), any())).thenReturn(sampleAnnouncement);

        mockMvc.perform(put("/api/announcements/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleAnnouncement)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void update_asEmployee_returns403() throws Exception {
        mockMvc.perform(put("/api/announcements/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleAnnouncement)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_notFound_returns404() throws Exception {
        when(service.update(eq(999L), any()))
                .thenThrow(new ResourceNotFoundException("Announcement", 999L));

        mockMvc.perform(put("/api/announcements/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleAnnouncement)))
                .andExpect(status().isNotFound());
    }

    // ── POST /api/announcements/{id}/view ─────────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void markAsRead_returns204() throws Exception {
        doNothing().when(service).markAsRead(1L, "emp@company.com");

        mockMvc.perform(post("/api/announcements/1/view"))
                .andExpect(status().isNoContent());
    }

    // ── GET /api/announcements/{id}/viewers ───────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getViewers_asAdmin_returns200() throws Exception {
        AnnouncementViewersDTO viewers = AnnouncementViewersDTO.builder()
                .totalViewed(5)
                .totalActive(10)
                .viewed(List.of())
                .notViewed(List.of())
                .build();
        when(service.getViewers(1L)).thenReturn(viewers);

        mockMvc.perform(get("/api/announcements/1/viewers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalViewed").value(5));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getViewers_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/announcements/1/viewers"))
                .andExpect(status().isForbidden());
    }

    // ── DELETE /api/announcements/{id} ────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_asAdmin_returns204() throws Exception {
        doNothing().when(service).delete(1L);

        mockMvc.perform(delete("/api/announcements/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void delete_asEmployee_returns403() throws Exception {
        mockMvc.perform(delete("/api/announcements/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Announcement", 999L)).when(service).delete(999L);

        mockMvc.perform(delete("/api/announcements/999"))
                .andExpect(status().isNotFound());
    }
}
