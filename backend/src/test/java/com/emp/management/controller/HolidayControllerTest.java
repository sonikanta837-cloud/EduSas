package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.dto.HolidayDTO;
import com.emp.management.exception.GlobalExceptionHandler;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.EmployeeDetailsRepository;
import com.emp.management.security.JwtAuthFilter;
import com.emp.management.service.HolidayService;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = HolidayController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class HolidayControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private HolidayService holidayService;
    @MockBean  private EmployeeDetailsRepository employeeDetailsRepository;

    private HolidayDTO sampleHoliday;

    @BeforeEach
    void setUp() {
        sampleHoliday = HolidayDTO.builder()
                .id(1L)
                .name("Republic Day")
                .date(LocalDate.of(2026, 1, 26))
                .holidayType("PUBLIC")
                .location("ALL")
                .active(true)
                .build();
    }

    // ── GET /api/holidays ─────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAll_asAdmin_returns200WithList() throws Exception {
        when(holidayService.getAll(null)).thenReturn(List.of(sampleHoliday));

        mockMvc.perform(get("/api/holidays"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Republic Day"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getAll_asEmployee_returns200() throws Exception {
        when(holidayService.getAll(null)).thenReturn(List.of(sampleHoliday));

        mockMvc.perform(get("/api/holidays"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAll_withYearFilter_returns200() throws Exception {
        when(holidayService.getAll(2026)).thenReturn(List.of(sampleHoliday));

        mockMvc.perform(get("/api/holidays").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value("2026-01-26"));
    }

    @Test
    void getAll_unauthenticated_returns401or403() throws Exception {
        mockMvc.perform(get("/api/holidays"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assert status == 401 || status == 403;
                });
    }

    // ── GET /api/holidays/my ──────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void getMy_authenticatedEmployee_returns200() throws Exception {
        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.empty());
        when(holidayService.getForEmployee(anyString(), any(), anyInt())).thenReturn(List.of(sampleHoliday));

        mockMvc.perform(get("/api/holidays/my").param("year", "2026"))
                .andExpect(status().isOk());
    }

    // ── GET /api/holidays/years ───────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getYears_returns200WithYearList() throws Exception {
        when(holidayService.getDistinctYears()).thenReturn(List.of(2024, 2025, 2026));

        mockMvc.perform(get("/api/holidays/years"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    // ── POST /api/holidays ────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_asAdmin_returns201WithDTO() throws Exception {
        when(holidayService.create(any())).thenReturn(sampleHoliday);

        mockMvc.perform(post("/api/holidays")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(sampleHoliday)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Republic Day"));
    }

    @Test
    @WithMockUser(roles = "HR")
    void create_asHR_returns201() throws Exception {
        when(holidayService.create(any())).thenReturn(sampleHoliday);

        mockMvc.perform(post("/api/holidays")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(sampleHoliday)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void create_asEmployee_returns403() throws Exception {
        mockMvc.perform(post("/api/holidays")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(sampleHoliday)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_missingName_returns400() throws Exception {
        HolidayDTO invalid = HolidayDTO.builder()
                .date(LocalDate.of(2026, 5, 1)).holidayType("PUBLIC").active(true).build();

        mockMvc.perform(post("/api/holidays")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_missingDate_returns400() throws Exception {
        HolidayDTO invalid = HolidayDTO.builder()
                .name("Test Holiday").holidayType("PUBLIC").active(true).build();

        mockMvc.perform(post("/api/holidays")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    // ── PUT /api/holidays/{id} ────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_asAdmin_returns200() throws Exception {
        when(holidayService.update(eq(1L), any())).thenReturn(sampleHoliday);

        mockMvc.perform(put("/api/holidays/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(sampleHoliday)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Republic Day"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_notFound_returns404() throws Exception {
        when(holidayService.update(eq(999L), any()))
                .thenThrow(new ResourceNotFoundException("Holiday", 999L));

        mockMvc.perform(put("/api/holidays/999")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(sampleHoliday)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void update_asManager_returns403() throws Exception {
        mockMvc.perform(put("/api/holidays/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(sampleHoliday)))
                .andExpect(status().isForbidden());
    }

    // ── DELETE /api/holidays/{id} ─────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_asAdmin_returns204() throws Exception {
        doNothing().when(holidayService).delete(1L);

        mockMvc.perform(delete("/api/holidays/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "HR")
    void delete_asHR_returns204() throws Exception {
        doNothing().when(holidayService).delete(1L);

        mockMvc.perform(delete("/api/holidays/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void delete_asEmployee_returns403() throws Exception {
        mockMvc.perform(delete("/api/holidays/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Holiday", 999L)).when(holidayService).delete(999L);

        mockMvc.perform(delete("/api/holidays/999"))
                .andExpect(status().isNotFound());
    }

    // ── POST /api/holidays/bulk ───────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void bulkCreate_asAdmin_returns200() throws Exception {
        HolidayDTO dto2 = HolidayDTO.builder().id(2L).name("Independence Day")
                .date(LocalDate.of(2026, 8, 15)).holidayType("PUBLIC").active(true).build();
        when(holidayService.bulkCreate(anyList())).thenReturn(List.of(sampleHoliday, dto2));

        mockMvc.perform(post("/api/holidays/bulk")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(List.of(sampleHoliday, dto2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void bulkCreate_asEmployee_returns403() throws Exception {
        mockMvc.perform(post("/api/holidays/bulk")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(List.of(sampleHoliday))))
                .andExpect(status().isForbidden());
    }
}
