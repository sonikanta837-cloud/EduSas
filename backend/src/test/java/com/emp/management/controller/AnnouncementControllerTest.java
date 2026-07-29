package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.dto.AnnouncementDTO;
import com.emp.management.exception.GlobalExceptionHandler;
import com.emp.management.security.JwtAuthFilter;
import com.emp.management.service.AnnouncementService;
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

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void getAll_anyAuthenticatedRole_returns200() throws Exception {
        when(service.getAll("emp@company.com")).thenReturn(List.of(
                AnnouncementDTO.builder().id(1L).title("Holiday notice").build()));

        mockMvc.perform(get("/api/announcements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(username = "hr@company.com", roles = "HR")
    void create_asHR_returns200() throws Exception {
        AnnouncementDTO dto = AnnouncementDTO.builder().title("New policy").build();
        when(service.create(any())).thenReturn(dto);

        mockMvc.perform(post("/api/announcements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void create_asEmployee_returns403() throws Exception {
        mockMvc.perform(post("/api/announcements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AnnouncementDTO.builder().title("x").build())))
                .andExpect(status().isForbidden());

        verify(service, never()).create(any());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void create_asManager_returns403() throws Exception {
        mockMvc.perform(post("/api/announcements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AnnouncementDTO.builder().title("x").build())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void markAsRead_returns204() throws Exception {
        doNothing().when(service).markAsRead(1L, "emp@company.com");

        mockMvc.perform(post("/api/announcements/1/view"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void getUnreadCount_returns200WithCount() throws Exception {
        when(service.getUnreadCount("emp@company.com")).thenReturn(3L);

        mockMvc.perform(get("/api/announcements/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));
    }

    @Test
    @WithMockUser(roles = "HR")
    void getViewers_asHR_returns200() throws Exception {
        when(service.getViewers(1L)).thenReturn(
                com.emp.management.dto.AnnouncementViewersDTO.builder().totalViewed(5).build());

        mockMvc.perform(get("/api/announcements/1/viewers"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getViewers_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/announcements/1/viewers"))
                .andExpect(status().isForbidden());
    }

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
}
