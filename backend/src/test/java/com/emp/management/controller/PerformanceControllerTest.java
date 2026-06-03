package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.dto.PerformanceReviewDTO;
import com.emp.management.exception.GlobalExceptionHandler;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.security.JwtAuthFilter;
import com.emp.management.service.PerformanceService;
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

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PerformanceController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class PerformanceControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private PerformanceService performanceService;

    private PerformanceReviewDTO sampleReview;

    @BeforeEach
    void setUp() {
        sampleReview = PerformanceReviewDTO.builder()
                .id(1L)
                .employeeId(2L)
                .employeeName("John Doe")
                .reviewerId(1L)
                .reviewerName("Admin User")
                .rating(4)
                .comments("Excellent work")
                .strengths("Communication")
                .areasOfImprovement("Time management")
                .reviewDate(LocalDate.of(2025, 1, 15))
                .reviewPeriod("Q1 2025")
                .build();
    }

    // ── DELETE /api/performance/{id} ─────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteReview_asAdmin_returns204NoContent() throws Exception {
        doNothing().when(performanceService).deleteReview(1L);

        mockMvc.perform(delete("/api/performance/1"))
                .andExpect(status().isNoContent());

        verify(performanceService).deleteReview(1L);
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void deleteReview_asManager_returns204NoContent() throws Exception {
        doNothing().when(performanceService).deleteReview(1L);

        mockMvc.perform(delete("/api/performance/1"))
                .andExpect(status().isNoContent());

        verify(performanceService).deleteReview(1L);
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_MANAGER")
    void deleteReview_asAssistantManager_returns204NoContent() throws Exception {
        doNothing().when(performanceService).deleteReview(1L);

        mockMvc.perform(delete("/api/performance/1"))
                .andExpect(status().isNoContent());

        verify(performanceService).deleteReview(1L);
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void deleteReview_asEmployee_returns403Forbidden() throws Exception {
        mockMvc.perform(delete("/api/performance/1"))
                .andExpect(status().isForbidden());

        verify(performanceService, never()).deleteReview(any());
    }

    @Test
    @WithMockUser(roles = "HR")
    void deleteReview_asHR_returns403Forbidden() throws Exception {
        mockMvc.perform(delete("/api/performance/1"))
                .andExpect(status().isForbidden());

        verify(performanceService, never()).deleteReview(any());
    }

    @Test
    void deleteReview_unauthenticated_returns401or403() throws Exception {
        mockMvc.perform(delete("/api/performance/1"))
                .andExpect(status().is(org.hamcrest.Matchers.anyOf(
                    org.hamcrest.Matchers.is(401),
                    org.hamcrest.Matchers.is(403)
                )));

        verify(performanceService, never()).deleteReview(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteReview_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("PerformanceReview", 999L))
                .when(performanceService).deleteReview(999L);

        mockMvc.perform(delete("/api/performance/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteReview_returns204_withEmptyBody() throws Exception {
        doNothing().when(performanceService).deleteReview(1L);

        mockMvc.perform(delete("/api/performance/1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    // ── POST /api/performance ────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void createReview_asAdmin_returns200WithDTO() throws Exception {
        when(performanceService.createReview(any())).thenReturn(sampleReview);

        mockMvc.perform(post("/api/performance")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(sampleReview)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeName").value("John Doe"))
                .andExpect(jsonPath("$.rating").value(4));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void createReview_asManager_returns200() throws Exception {
        when(performanceService.createReview(any())).thenReturn(sampleReview);

        mockMvc.perform(post("/api/performance")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(sampleReview)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void createReview_asEmployee_returns403Forbidden() throws Exception {
        mockMvc.perform(post("/api/performance")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(sampleReview)))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/performance ─────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllReviews_asAdmin_returns200WithList() throws Exception {
        when(performanceService.getAllReviews()).thenReturn(List.of(sampleReview));

        mockMvc.perform(get("/api/performance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].employeeName").value("John Doe"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void getAllReviews_asManager_returns200() throws Exception {
        when(performanceService.getAllReviews()).thenReturn(List.of(sampleReview));
        mockMvc.perform(get("/api/performance"))
                .andExpect(status().isOk());
    }

    // ── GET /api/performance/employee/{employeeId} ───────────────────────────

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getEmployeeReviews_asEmployee_returns200() throws Exception {
        when(performanceService.getEmployeeReviews(2L)).thenReturn(List.of(sampleReview));

        mockMvc.perform(get("/api/performance/employee/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ── GET /api/performance/employee/{employeeId}/average ───────────────────

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getAverageRating_withValue_returns200WithAvg() throws Exception {
        when(performanceService.getAverageRating(2L)).thenReturn(4.0);

        mockMvc.perform(get("/api/performance/employee/2/average"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(4.0))
                .andExpect(jsonPath("$.employeeId").value(2));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getAverageRating_noReviews_returns0() throws Exception {
        when(performanceService.getAverageRating(99L)).thenReturn(null);

        mockMvc.perform(get("/api/performance/employee/99/average"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(0.0));
    }
}
