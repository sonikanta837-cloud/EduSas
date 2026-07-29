package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.dto.CourseDTO;
import com.emp.management.entity.EmployeeDetails;
import com.emp.management.entity.Role;
import com.emp.management.entity.User;
import com.emp.management.exception.GlobalExceptionHandler;
import com.emp.management.repository.EmployeeDetailsRepository;
import com.emp.management.security.JwtAuthFilter;
import com.emp.management.service.AIQuizService;
import com.emp.management.service.CourseService;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CourseController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class CourseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private CourseService courseService;
    @MockBean private AIQuizService aiQuizService;
    @MockBean private EmployeeDetailsRepository employeeDetailsRepository;

    private EmployeeDetails employeeWithRole(String email, long id, Role role, EmployeeDetails manager) {
        User user = User.builder().id(id).email(email).role(role).active(true).build();
        return EmployeeDetails.builder().id(id).user(user).manager(manager).active(true).build();
    }

    // ── GET /api/courses ───────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getAllCourses_anyAuthenticatedRole_returns200() throws Exception {
        when(courseService.getAllCourses()).thenReturn(List.of(CourseDTO.builder().id(1L).title("Spring Boot").build()));

        mockMvc.perform(get("/api/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ── GET /api/courses/all-enrollments ─────────────────────────────────────

    @Test
    @WithMockUser(roles = "MANAGER")
    void getAllEnrollments_asManager_returns403() throws Exception {
        mockMvc.perform(get("/api/courses/all-enrollments"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HR")
    void getAllEnrollments_asHR_returns200() throws Exception {
        when(courseService.getAllEnrollments()).thenReturn(List.of());

        mockMvc.perform(get("/api/courses/all-enrollments"))
                .andExpect(status().isOk());
    }

    // ── POST /api/courses ──────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "HR")
    void createCourse_asHR_returns200AndTriggersQuizGeneration() throws Exception {
        CourseDTO dto = CourseDTO.builder().title("New Course").build();
        CourseDTO saved = CourseDTO.builder().id(5L).title("New Course").build();
        when(courseService.createCourse(any(), eq(1L))).thenReturn(saved);

        mockMvc.perform(post("/api/courses").param("createdBy", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));

        verify(aiQuizService).generateAndSaveQuiz(5L);
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void createCourse_asEmployee_returns403() throws Exception {
        mockMvc.perform(post("/api/courses").param("createdBy", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CourseDTO.builder().title("x").build())))
                .andExpect(status().isForbidden());

        verify(courseService, never()).createCourse(any(), any());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void createCourse_asManager_returns403() throws Exception {
        mockMvc.perform(post("/api/courses").param("createdBy", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CourseDTO.builder().title("x").build())))
                .andExpect(status().isForbidden());
    }

    // ── DELETE /api/courses/{id} ──────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteCourse_asAdmin_returns200() throws Exception {
        doNothing().when(courseService).deleteCourse(1L);

        mockMvc.perform(delete("/api/courses/1"))
                .andExpect(status().isOk())
                .andExpect(content().string("Course deleted"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void deleteCourse_asManager_returns403() throws Exception {
        mockMvc.perform(delete("/api/courses/1"))
                .andExpect(status().isForbidden());
    }

    // ── POST /api/courses/{courseId}/enroll/{employeeId} — open to all ──────

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void enroll_anyAuthenticatedRole_returns200() throws Exception {
        doNothing().when(courseService).enrollEmployee(1L, 2L);

        mockMvc.perform(post("/api/courses/1/enroll/2"))
                .andExpect(status().isOk())
                .andExpect(content().string("Enrolled successfully"));
    }

    // ── POST /api/courses/{courseId}/assign ───────────────────────────────────

    @Test
    @WithMockUser(username = "mgr@company.com", roles = "MANAGER")
    void assignCourse_asManager_returns200() throws Exception {
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com")).thenReturn(Optional.empty());
        doNothing().when(courseService).assignCourse(eq(1L), eq(2L), eq(false), anyString());

        mockMvc.perform(post("/api/courses/1/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("employeeId", 2))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void assignCourse_asEmployee_returns403() throws Exception {
        mockMvc.perform(post("/api/courses/1/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("employeeId", 2))))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/courses/{courseId}/enrolled-employee-ids ────────────────────

    @Test
    @WithMockUser(roles = "MANAGER")
    void getEnrolledEmployeeIds_asManager_returns200() throws Exception {
        when(courseService.getEnrolledEmployeeIds(1L)).thenReturn(List.of(2L, 3L));

        mockMvc.perform(get("/api/courses/1/enrolled-employee-ids"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getEnrolledEmployeeIds_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/courses/1/enrolled-employee-ids"))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/courses/employee/{employeeId} — hierarchy access check ─────

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void getCoursesForEmployee_self_returns200() throws Exception {
        EmployeeDetails self = employeeWithRole("emp@company.com", 2L, Role.EMPLOYEE, null);
        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(self));
        when(courseService.getCoursesForEmployee(2L)).thenReturn(List.of());

        mockMvc.perform(get("/api/courses/employee/2"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "emp@company.com", roles = "EMPLOYEE")
    void getCoursesForEmployee_unrelatedEmployee_returns403() throws Exception {
        EmployeeDetails self = employeeWithRole("emp@company.com", 2L, Role.EMPLOYEE, null);
        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(self));

        mockMvc.perform(get("/api/courses/employee/999"))
                .andExpect(status().isForbidden());

        verify(courseService, never()).getCoursesForEmployee(any());
    }

    @Test
    @WithMockUser(username = "mgr@company.com", roles = "MANAGER")
    void getCoursesForEmployee_managerViewingDirectReport_returns200() throws Exception {
        EmployeeDetails manager = employeeWithRole("mgr@company.com", 1L, Role.MANAGER, null);
        EmployeeDetails directReport = employeeWithRole("emp@company.com", 2L, Role.EMPLOYEE, manager);
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com")).thenReturn(Optional.of(manager));
        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(directReport));
        when(courseService.getCoursesForEmployee(2L)).thenReturn(List.of());

        mockMvc.perform(get("/api/courses/employee/2"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "mgr@company.com", roles = "MANAGER")
    void getCoursesForEmployee_managerViewingNonReport_returns403() throws Exception {
        EmployeeDetails manager = employeeWithRole("mgr@company.com", 1L, Role.MANAGER, null);
        EmployeeDetails otherManager = employeeWithRole("othermgr@company.com", 9L, Role.MANAGER, null);
        EmployeeDetails notMyReport = employeeWithRole("stranger@company.com", 2L, Role.EMPLOYEE, otherManager);
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com")).thenReturn(Optional.of(manager));
        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(notMyReport));

        mockMvc.perform(get("/api/courses/employee/2"))
                .andExpect(status().isForbidden());

        verify(courseService, never()).getCoursesForEmployee(any());
    }

    @Test
    @WithMockUser(username = "hr@company.com", roles = "HR")
    void getCoursesForEmployee_asHR_bypassesHierarchyCheck() throws Exception {
        EmployeeDetails hr = employeeWithRole("hr@company.com", 1L, Role.HR, null);
        when(employeeDetailsRepository.findByUserEmail("hr@company.com")).thenReturn(Optional.of(hr));
        when(courseService.getCoursesForEmployee(999L)).thenReturn(List.of());

        mockMvc.perform(get("/api/courses/employee/999"))
                .andExpect(status().isOk());
    }

    // ── POST /api/courses/{courseId}/exam/{employeeId} — open to all ────────

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void submitExam_anyAuthenticatedRole_returns200() throws Exception {
        when(courseService.submitExam(eq(1L), eq(2L), anyList()))
                .thenReturn(Map.of("passed", true, "score", 90));

        mockMvc.perform(post("/api/courses/1/exam/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("answers", List.of(1, 2, 3)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true));
    }

    // ── POST /api/courses/{courseId}/exam — create exam ───────────────────────

    @Test
    @WithMockUser(roles = "HR")
    void createExam_asHR_returns200() throws Exception {
        doNothing().when(courseService).createExam(eq(1L), any(), eq(70));

        mockMvc.perform(post("/api/courses/1/exam")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("questions", List.of(), "passingScore", 70))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void createExam_asManager_returns403() throws Exception {
        mockMvc.perform(post("/api/courses/1/exam")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("questions", List.of(), "passingScore", 70))))
                .andExpect(status().isForbidden());
    }
}
