package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.entity.FAQ;
import com.emp.management.exception.GlobalExceptionHandler;
import com.emp.management.security.JwtAuthFilter;
import com.emp.management.service.FaqService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = FaqController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class FaqControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private FaqService faqService;

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getAll_defaultAllFalse_returnsActiveOnly() throws Exception {
        when(faqService.getAll(false)).thenReturn(List.of(new FAQ()));

        mockMvc.perform(get("/api/faqs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        verify(faqService).getAll(false);
    }

    @Test
    @WithMockUser(roles = "HR")
    void getAll_allTrueWithAuthenticatedPrincipal_requestsAdminView() throws Exception {
        when(faqService.getAll(true)).thenReturn(List.of(new FAQ(), new FAQ()));

        mockMvc.perform(get("/api/faqs").param("all", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        verify(faqService).getAll(true);
    }

    @Test
    @WithMockUser(username = "hr@company.com", roles = "HR")
    void create_asHR_returns200() throws Exception {
        when(faqService.create(any(), eq("hr@company.com"))).thenReturn(new FAQ());

        mockMvc.perform(post("/api/faqs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("question", "Q?", "answer", "A."))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void create_asEmployee_returns403() throws Exception {
        mockMvc.perform(post("/api/faqs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("question", "Q?", "answer", "A."))))
                .andExpect(status().isForbidden());

        verify(faqService, never()).create(any(), any());
    }

    @Test
    @WithMockUser(roles = "HR")
    void toggle_asHR_returns200() throws Exception {
        when(faqService.toggleActive(1L)).thenReturn(new FAQ());

        mockMvc.perform(patch("/api/faqs/1/toggle"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void toggle_asManager_returns403() throws Exception {
        mockMvc.perform(patch("/api/faqs/1/toggle"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_asAdmin_returns204() throws Exception {
        doNothing().when(faqService).delete(1L);

        mockMvc.perform(delete("/api/faqs/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void delete_asEmployee_returns403() throws Exception {
        mockMvc.perform(delete("/api/faqs/1"))
                .andExpect(status().isForbidden());
    }
}
