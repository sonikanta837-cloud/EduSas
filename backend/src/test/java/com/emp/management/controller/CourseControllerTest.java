package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.dto.CourseDTO;
import com.emp.management.entity.EmployeeDetails;
import com.emp.management.entity.Role;
import com.emp.management.entity.User;
import com.emp.management.exception.GlobalExceptionHandler;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.EmployeeDetailsRepository;
import com.emp.management.security.JwtAuthFilter;
import com.emp.management.service.AIQuizService;
import com.emp.management.service.CourseService;
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

    private CourseDTO sampleCourse;
    private EmployeeDetails adminEmp;

    @BeforeEach
    void setUp() {
        sampleCourse = CourseDTO.builder()
                .id(1L)
                .title("Spring Boot Fundamentals")
                .description("Learn Spring Boot")
                .active(true)
                .enrollmentCount(5)
                .completedCount(2)
                .inProgressCount(3)
                .enrolledEmployeeNames(List.of("Alice Smith", "Bob Jones"))
                .build();

        User adminUser = User.builder().id(1L).email("admin@company.com").role(Role.ADMIN).active(true).build();
        adminEmp = EmployeeDetails.builder().id(1L).user(adminUser).firstName("Admin").lastName("User")
                .active(true).build();
        adminUser.setEmployeeDetails(adminEmp);
    }

    // ── GET /api/courses ──────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getAllCourses_authenticated_returns200() throws Exception {
        when(courseService.getAllCourses()).thenReturn(List.of(sampleCourse));

        mockMvc.perform(get("/api/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Spring Boot Fundamentals"));
    }

    @Test
    void getAllCourses_unauthenticated_returns401or403() throws Exception {
        mockMvc.perform(get("/api/courses"))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s != 401 && s != 403) throw new AssertionError("Expected 401 or 403, got: " + s);
                });
    }

    // ── GET /api/courses/{id} ─────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getCourse_existingId_returns200() throws Exception {
        when(courseService.getCourseById(1L)).thenReturn(sampleCourse);

        mockMvc.perform(get("/api/courses/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Spring Boot Fundamentals"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getCourse_notFound_returns404() throws Exception {
        when(courseService.getCourseById(999L)).thenThrow(new ResourceNotFoundException("Course", 999L));

        mockMvc.perform(get("/api/courses/999"))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/courses/employee/{id} ───────────────────────────────────────

    @Test
    @WithMockUser(username = "admin@company.com", roles = "ADMIN")
    void getCoursesForEmployee_asAdmin_returns200() throws Exception {
        when(employeeDetailsRepository.findByUserEmail("admin@company.com")).thenReturn(Optional.of(adminEmp));
        when(courseService.getCoursesForEmployee(1L)).thenReturn(List.of(sampleCourse));

        mockMvc.perform(get("/api/courses/employee/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ── GET /api/courses/manager/{id} ────────────────────────────────────────

    @Test
    @WithMockUser(roles = "MANAGER")
    void getCoursesForManager_asManager_returns200() throws Exception {
        when(courseService.getCoursesForManager(1L)).thenReturn(List.of(sampleCourse));

        mockMvc.perform(get("/api/courses/manager/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getCoursesForManager_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/courses/manager/1"))
                .andExpect(status().isForbidden());
    }

    // ── POST /api/courses ─────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void createCourse_asAdmin_returns200() throws Exception {
        when(courseService.createCourse(any(), eq(1L))).thenReturn(sampleCourse);
        doNothing().when(aiQuizService).generateAndSaveQuiz(anyLong());

        mockMvc.perform(post("/api/courses")
                        .param("createdBy", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleCourse)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Spring Boot Fundamentals"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void createCourse_asEmployee_returns403() throws Exception {
        mockMvc.perform(post("/api/courses")
                        .param("createdBy", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleCourse)))
                .andExpect(status().isForbidden());
    }

    // ── PUT /api/courses/{id} ─────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateCourse_asAdmin_returns200() throws Exception {
        when(courseService.updateCourse(eq(1L), any())).thenReturn(sampleCourse);

        mockMvc.perform(put("/api/courses/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleCourse)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Spring Boot Fundamentals"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void updateCourse_asManager_returns403() throws Exception {
        mockMvc.perform(put("/api/courses/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleCourse)))
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
    @WithMockUser(roles = "HR")
    void deleteCourse_asHR_returns403() throws Exception {
        mockMvc.perform(delete("/api/courses/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteCourse_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Course", 999L)).when(courseService).deleteCourse(999L);

        mockMvc.perform(delete("/api/courses/999"))
                .andExpect(status().isNotFound());
    }

    // ── POST /api/courses/{id}/enroll/{empId} ─────────────────────────────────

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void enroll_validRequest_returns200() throws Exception {
        doNothing().when(courseService).enrollEmployee(1L, 2L);

        mockMvc.perform(post("/api/courses/1/enroll/2"))
                .andExpect(status().isOk())
                .andExpect(content().string("Enrolled successfully"));
    }

    // ── POST /api/courses/{id}/watch/{empId} ──────────────────────────────────

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void markVideoWatched_returns200() throws Exception {
        doNothing().when(courseService).markVideoWatched(1L, 2L);

        mockMvc.perform(post("/api/courses/1/watch/2"))
                .andExpect(status().isOk())
                .andExpect(content().string("Progress updated"));
    }

    // ── GET /api/courses/{id}/enrolled-employee-ids ───────────────────────────

    @Test
    @WithMockUser(roles = "MANAGER")
    void getEnrolledEmployeeIds_asManager_returns200() throws Exception {
        when(courseService.getEnrolledEmployeeIds(1L)).thenReturn(List.of(10L, 11L, 12L));

        mockMvc.perform(get("/api/courses/1/enrolled-employee-ids"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getEnrolledEmployeeIds_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/courses/1/enrolled-employee-ids"))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/courses/all-enrollments ─────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllEnrollments_asAdmin_returns200() throws Exception {
        when(courseService.getAllEnrollments()).thenReturn(List.of());

        mockMvc.perform(get("/api/courses/all-enrollments"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getAllEnrollments_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/courses/all-enrollments"))
                .andExpect(status().isForbidden());
    }

    // ── POST /api/courses/{id}/assign ─────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin@company.com", roles = "ADMIN")
    void assignCourse_asAdmin_returns200() throws Exception {
        when(employeeDetailsRepository.findByUserEmail("admin@company.com")).thenReturn(Optional.of(adminEmp));
        doNothing().when(courseService).assignCourse(anyLong(), any(), anyBoolean(), anyString());

        Map<String, Object> body = Map.of("employeeId", 2, "assignAll", false);

        mockMvc.perform(post("/api/courses/1/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(content().string("Course assigned successfully"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void assignCourse_asEmployee_returns403() throws Exception {
        mockMvc.perform(post("/api/courses/1/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // ── POST /api/courses/{id}/exam ───────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void createExam_asAdmin_returns200() throws Exception {
        doNothing().when(courseService).createExam(anyLong(), any(), anyInt());

        Map<String, Object> body = Map.of("questions", List.of(), "passingScore", 70);

        mockMvc.perform(post("/api/courses/1/exam")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(content().string("Exam created"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void createExam_asEmployee_returns403() throws Exception {
        Map<String, Object> body = Map.of("questions", List.of(), "passingScore", 70);

        mockMvc.perform(post("/api/courses/1/exam")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    // ── POST /api/courses/{id}/exam/{empId} ──────────────────────────────────

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void submitExam_returns200() throws Exception {
        when(courseService.submitExam(anyLong(), anyLong(), any()))
                .thenReturn(Map.of("score", 85, "passed", true));

        Map<String, Object> body = Map.of("answers", List.of(1, 2, 3));

        mockMvc.perform(post("/api/courses/1/exam/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(85))
                .andExpect(jsonPath("$.passed").value(true));
    }
}
