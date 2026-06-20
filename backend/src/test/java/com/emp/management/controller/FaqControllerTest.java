package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.entity.FAQ;
import com.emp.management.exception.GlobalExceptionHandler;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.security.JwtAuthFilter;
import com.emp.management.service.FaqService;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = FaqController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class FaqControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private FaqService faqService;

    private FAQ sampleFaq;

    @BeforeEach
    void setUp() {
        sampleFaq = FAQ.builder()
                .id(1L)
                .question("What is the leave policy?")
                .answer("15 days per year.")
                .displayOrder(1)
                .active(true)
                .createdBy("admin@company.com")
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ── GET /api/faqs ─────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getAll_asEmployee_returns200WithFaqs() throws Exception {
        when(faqService.getAll(false)).thenReturn(List.of(sampleFaq));

        mockMvc.perform(get("/api/faqs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].question").value("What is the leave policy?"));
    }

    @Test
    @WithMockUser(username = "admin@company.com", roles = "ADMIN")
    void getAll_withAllParam_asAdmin_returnsAllFaqs() throws Exception {
        when(faqService.getAll(true)).thenReturn(List.of(sampleFaq));

        mockMvc.perform(get("/api/faqs").param("all", "true"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getAll_emptyList_returns200WithEmptyArray() throws Exception {
        when(faqService.getAll(false)).thenReturn(List.of());

        mockMvc.perform(get("/api/faqs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── POST /api/faqs ────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin@company.com", roles = "ADMIN")
    void create_asAdmin_returns200() throws Exception {
        when(faqService.create(any(), any())).thenReturn(sampleFaq);

        mockMvc.perform(post("/api/faqs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Q?\",\"answer\":\"A.\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "HR")
    void create_asHr_returns200() throws Exception {
        when(faqService.create(any(), any())).thenReturn(sampleFaq);

        mockMvc.perform(post("/api/faqs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Q?\",\"answer\":\"A.\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void create_asEmployee_returns403() throws Exception {
        mockMvc.perform(post("/api/faqs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Q?\",\"answer\":\"A.\"}"))
                .andExpect(status().isForbidden());
    }

    // ── PUT /api/faqs/{id} ────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_asAdmin_returns200() throws Exception {
        when(faqService.update(eq(1L), any())).thenReturn(sampleFaq);

        mockMvc.perform(put("/api/faqs/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Updated?\",\"answer\":\"Updated.\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_notFound_returns404() throws Exception {
        when(faqService.update(eq(999L), any()))
                .thenThrow(new ResourceNotFoundException("FAQ not found: 999"));

        mockMvc.perform(put("/api/faqs/999")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"X\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void update_asEmployee_returns403() throws Exception {
        mockMvc.perform(put("/api/faqs/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"X\"}"))
                .andExpect(status().isForbidden());
    }

    // ── PATCH /api/faqs/{id}/toggle ───────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void toggle_asAdmin_returns200() throws Exception {
        FAQ toggled = FAQ.builder().id(1L).question("Q?").answer("A.").active(false).build();
        when(faqService.toggleActive(1L)).thenReturn(toggled);

        mockMvc.perform(patch("/api/faqs/1/toggle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void toggle_asEmployee_returns403() throws Exception {
        mockMvc.perform(patch("/api/faqs/1/toggle"))
                .andExpect(status().isForbidden());
    }

    // ── DELETE /api/faqs/{id} ─────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_asAdmin_returns204() throws Exception {
        doNothing().when(faqService).delete(1L);

        mockMvc.perform(delete("/api/faqs/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("FAQ not found: 999"))
                .when(faqService).delete(999L);

        mockMvc.perform(delete("/api/faqs/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void delete_asManager_returns403() throws Exception {
        mockMvc.perform(delete("/api/faqs/1"))
                .andExpect(status().isForbidden());
    }

    // ── Unauthenticated ───────────────────────────────────────────────────────

    @Test
    void create_unauthenticated_returns401or403() throws Exception {
        mockMvc.perform(post("/api/faqs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Q?\",\"answer\":\"A.\"}"))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s != 401 && s != 403) throw new AssertionError("Expected 401 or 403, got: " + s);
                });
    }
}
