package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.entity.FAQ;
import com.emp.management.exception.GlobalExceptionHandler;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.security.JwtAuthFilter;
import com.emp.management.service.FaqService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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
    @MockBean  private FaqService faqService;

    private FAQ activeFaq;
    private FAQ inactiveFaq;

    @BeforeEach
    void setUp() {
        activeFaq = FAQ.builder()
                .id(1L)
                .question("What is the leave policy?")
                .answer("Employees get 21 days of paid leave per year.")
                .active(true)
                .displayOrder(1)
                .createdBy("admin@company.com")
                .createdAt(LocalDateTime.now())
                .build();

        inactiveFaq = FAQ.builder()
                .id(2L)
                .question("How do I apply for reimbursement?")
                .answer("Submit a reimbursement form.")
                .active(false)
                .displayOrder(2)
                .createdBy("hr@company.com")
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ── GET /api/faqs ─────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getAll_asEmployee_returns200WithActiveFaqs() throws Exception {
        when(faqService.getAll(false)).thenReturn(List.of(activeFaq));

        mockMvc.perform(get("/api/faqs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].question").value("What is the leave policy?"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAll_asAdmin_returns200WithAllFaqs() throws Exception {
        when(faqService.getAll(true)).thenReturn(List.of(activeFaq, inactiveFaq));

        mockMvc.perform(get("/api/faqs").param("all", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @WithMockUser(roles = "HR")
    void getAll_asHR_withAllTrue_returns200() throws Exception {
        when(faqService.getAll(true)).thenReturn(List.of(activeFaq, inactiveFaq));

        mockMvc.perform(get("/api/faqs").param("all", "true"))
                .andExpect(status().isOk());
    }

    @Test
    void getAll_unauthenticated_returns401or403() throws Exception {
        mockMvc.perform(get("/api/faqs"))
                .andExpect(result ->
                    assertThat(result.getResponse().getStatus()).isIn(401, 403));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getAll_defaultParam_returnsActiveOnly() throws Exception {
        when(faqService.getAll(false)).thenReturn(List.of(activeFaq));

        mockMvc.perform(get("/api/faqs"))
                .andExpect(status().isOk());

        verify(faqService).getAll(false);
    }

    // ── POST /api/faqs ────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN", username = "admin@company.com")
    void create_asAdmin_returns200() throws Exception {
        Map<String, Object> body = Map.of(
                "question", "How do I request leave?",
                "answer", "Go to the Leaves module.",
                "displayOrder", 1
        );
        when(faqService.create(any(), eq("admin@company.com"))).thenReturn(activeFaq);

        mockMvc.perform(post("/api/faqs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question").value("What is the leave policy?"));
    }

    @Test
    @WithMockUser(roles = "HR", username = "hr@company.com")
    void create_asHR_returns200() throws Exception {
        Map<String, Object> body = Map.of("question", "Q", "answer", "A");
        when(faqService.create(any(), eq("hr@company.com"))).thenReturn(activeFaq);

        mockMvc.perform(post("/api/faqs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void create_asEmployee_returns403() throws Exception {
        Map<String, Object> body = Map.of("question", "Q", "answer", "A");

        mockMvc.perform(post("/api/faqs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void create_asManager_returns403() throws Exception {
        Map<String, Object> body = Map.of("question", "Q", "answer", "A");

        mockMvc.perform(post("/api/faqs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    // ── PUT /api/faqs/{id} ────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "HR")
    void update_asHR_returns200() throws Exception {
        Map<String, Object> body = Map.of("question", "Updated Q", "answer", "Updated A");
        when(faqService.update(eq(1L), any())).thenReturn(activeFaq);

        mockMvc.perform(put("/api/faqs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_asAdmin_returns200() throws Exception {
        Map<String, Object> body = Map.of("answer", "New answer");
        when(faqService.update(eq(1L), any())).thenReturn(activeFaq);

        mockMvc.perform(put("/api/faqs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void update_asManager_returns403() throws Exception {
        Map<String, Object> body = Map.of("question", "Q");

        mockMvc.perform(put("/api/faqs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void update_asEmployee_returns403() throws Exception {
        Map<String, Object> body = Map.of("question", "Q");

        mockMvc.perform(put("/api/faqs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_faqNotFound_returns404() throws Exception {
        when(faqService.update(eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("FAQ", 99L));

        mockMvc.perform(put("/api/faqs/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("question", "Q"))))
                .andExpect(status().isNotFound());
    }

    // ── PATCH /api/faqs/{id}/toggle ───────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void toggle_asAdmin_returns200() throws Exception {
        FAQ toggled = FAQ.builder().id(1L).question("Q").answer("A").active(false).build();
        when(faqService.toggleActive(1L)).thenReturn(toggled);

        mockMvc.perform(patch("/api/faqs/1/toggle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @WithMockUser(roles = "HR")
    void toggle_asHR_returns200() throws Exception {
        when(faqService.toggleActive(1L)).thenReturn(activeFaq);

        mockMvc.perform(patch("/api/faqs/1/toggle"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void toggle_asEmployee_returns403() throws Exception {
        mockMvc.perform(patch("/api/faqs/1/toggle"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void toggle_asManager_returns403() throws Exception {
        mockMvc.perform(patch("/api/faqs/1/toggle"))
                .andExpect(status().isForbidden());
    }

    // ── DELETE /api/faqs/{id} ─────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "HR")
    void delete_asHR_returns204() throws Exception {
        doNothing().when(faqService).delete(1L);

        mockMvc.perform(delete("/api/faqs/1"))
                .andExpect(status().isNoContent());
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

    @Test
    @WithMockUser(roles = "MANAGER")
    void delete_asManager_returns403() throws Exception {
        mockMvc.perform(delete("/api/faqs/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_faqNotFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("FAQ", 99L))
                .when(faqService).delete(99L);

        mockMvc.perform(delete("/api/faqs/99"))
                .andExpect(status().isNotFound());
    }
}
