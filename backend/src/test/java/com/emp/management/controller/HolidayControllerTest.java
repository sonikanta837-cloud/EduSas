package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.dto.HolidayDTO;
import com.emp.management.entity.EmployeeDetails;
import com.emp.management.exception.GlobalExceptionHandler;
import com.emp.management.repository.EmployeeDetailsRepository;
import com.emp.management.security.JwtAuthFilter;
import com.emp.management.service.HolidayService;
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
    @MockBean private HolidayService holidayService;
    @MockBean private EmployeeDetailsRepository employeeDetailsRepository;

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getAll_anyAuthenticatedRole_returns200() throws Exception {
        when(holidayService.getAll(null)).thenReturn(List.of(HolidayDTO.builder().id(1L).name("Diwali").build()));

        mockMvc.perform(get("/api/holidays"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void getMy_returnsHolidaysForOwnSeatingLocation() throws Exception {
        EmployeeDetails emp = EmployeeDetails.builder().id(1L).seatingLocation("Pune").department("Engineering").build();
        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(emp));
        when(holidayService.getForEmployee(eq("Pune"), eq("Engineering"), anyInt()))
                .thenReturn(List.of(HolidayDTO.builder().id(1L).name("Diwali").build()));

        mockMvc.perform(get("/api/holidays/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(username = "ghost@company.com", roles = "EMPLOYEE")
    void getMy_noEmployeeRecord_fallsBackToAllLocation() throws Exception {
        when(employeeDetailsRepository.findByUserEmail("ghost@company.com")).thenReturn(Optional.empty());
        when(holidayService.getForEmployee(eq("ALL"), isNull(), anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/api/holidays/my"))
                .andExpect(status().isOk());

        verify(holidayService).getForEmployee(eq("ALL"), isNull(), anyInt());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_validHoliday_returns201() throws Exception {
        HolidayDTO dto = HolidayDTO.builder().name("Diwali").date(LocalDate.now()).holidayType("Festival").build();
        when(holidayService.create(any())).thenReturn(dto);

        mockMvc.perform(post("/api/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_blankName_returns400() throws Exception {
        HolidayDTO dto = HolidayDTO.builder().name("").date(LocalDate.now()).holidayType("Festival").build();

        mockMvc.perform(post("/api/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_missingDate_returns400() throws Exception {
        HolidayDTO dto = HolidayDTO.builder().name("Diwali").holidayType("Festival").build();

        mockMvc.perform(post("/api/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void create_asManager_returns403() throws Exception {
        HolidayDTO dto = HolidayDTO.builder().name("Diwali").date(LocalDate.now()).holidayType("Festival").build();

        mockMvc.perform(post("/api/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HR")
    void bulkCreate_asHR_returns200() throws Exception {
        HolidayDTO dto = HolidayDTO.builder().name("Diwali").date(LocalDate.now()).holidayType("Festival").build();
        when(holidayService.bulkCreate(any())).thenReturn(List.of(dto));

        mockMvc.perform(post("/api/holidays/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(dto))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void bulkCreate_asEmployee_returns403() throws Exception {
        HolidayDTO dto = HolidayDTO.builder().name("Diwali").date(LocalDate.now()).holidayType("Festival").build();

        mockMvc.perform(post("/api/holidays/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(dto))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_asAdmin_returns204() throws Exception {
        doNothing().when(holidayService).delete(1L);

        mockMvc.perform(delete("/api/holidays/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getYears_anyAuthenticatedRole_returns200() throws Exception {
        when(holidayService.getDistinctYears()).thenReturn(List.of(2026, 2025));

        mockMvc.perform(get("/api/holidays/years"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
