package com.emp.management.service;

import com.emp.management.dto.CourseDTO;
import com.emp.management.entity.*;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.*;
import com.emp.management.service.CourseNotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseServiceFilterTest {

    @Mock private CourseRepository courseRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private CertificateRepository certificateRepository;
    @Mock private ExamRepository examRepository;
    @Mock private EmployeeDetailsRepository employeeDetailsRepository;
    @Mock private UserRepository userRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private CourseNotificationService courseNotificationService;

    @InjectMocks private CourseService courseService;

    private Course course;
    private EmployeeDetails activeEmp1;
    private EmployeeDetails activeEmp2;
    private EmployeeDetails inactiveEmp;
    private Enrollment enrollmentActive1;
    private Enrollment enrollmentActive2;
    private Enrollment enrollmentInactive;
    private Enrollment enrollmentNullEmployee;

    @BeforeEach
    void setUp() {
        User user1 = User.builder().id(1L).email("a@company.com").role(Role.EMPLOYEE).active(true).build();
        User user2 = User.builder().id(2L).email("b@company.com").role(Role.EMPLOYEE).active(true).build();
        User user3 = User.builder().id(3L).email("c@company.com").role(Role.EMPLOYEE).active(false).build();

        activeEmp1 = EmployeeDetails.builder().id(10L).user(user1).firstName("Alice").lastName("Smith").active(true).build();
        activeEmp2 = EmployeeDetails.builder().id(11L).user(user2).firstName("Bob").lastName("Jones").active(true).build();
        inactiveEmp = EmployeeDetails.builder().id(12L).user(user3).firstName("Carol").lastName("White").active(false).build();

        course = Course.builder()
                .id(5L)
                .title("Spring Boot Fundamentals")
                .description("Learn Spring Boot")
                .active(true)
                .build();

        enrollmentActive1 = Enrollment.builder()
                .id(1L).employee(activeEmp1).course(course).status(EnrollmentStatus.COMPLETED).build();
        enrollmentActive2 = Enrollment.builder()
                .id(2L).employee(activeEmp2).course(course).status(EnrollmentStatus.IN_PROGRESS).build();
        enrollmentInactive = Enrollment.builder()
                .id(3L).employee(inactiveEmp).course(course).status(EnrollmentStatus.COMPLETED).build();
        enrollmentNullEmployee = Enrollment.builder()
                .id(4L).employee(null).course(course).status(EnrollmentStatus.ENROLLED).build();
    }

    // ── toDTO — active employees included ────────────────────────────────────

    @Test
    void toDTO_activeEmployeesIncluded_inEnrollmentCount() {
        course.setEnrollments(List.of(enrollmentActive1, enrollmentActive2));

        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));

        CourseDTO result = courseService.getCourseById(5L);

        assertThat(result.getEnrollmentCount()).isEqualTo(2);
        assertThat(result.getEnrolledEmployeeNames()).contains("Alice Smith", "Bob Jones");
    }

    @Test
    void toDTO_activeEmployees_completedCountCorrect() {
        course.setEnrollments(List.of(enrollmentActive1, enrollmentActive2));

        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));

        CourseDTO result = courseService.getCourseById(5L);

        assertThat(result.getCompletedCount()).isEqualTo(1);
        assertThat(result.getInProgressCount()).isEqualTo(1);
    }

    // ── toDTO — inactive employees excluded ──────────────────────────────────

    @Test
    void toDTO_inactiveEmployeesExcluded_fromEnrollmentCount() {
        course.setEnrollments(List.of(enrollmentActive1, enrollmentInactive));

        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));

        CourseDTO result = courseService.getCourseById(5L);

        // Only active employee (activeEmp1) should be counted
        assertThat(result.getEnrollmentCount()).isEqualTo(1);
        assertThat(result.getEnrolledEmployeeNames()).contains("Alice Smith");
        assertThat(result.getEnrolledEmployeeNames()).doesNotContain("Carol White");
    }

    @Test
    void toDTO_inactiveEmployeeCompletion_notCountedInCompletedCount() {
        // inactiveEmp has COMPLETED status but should not be counted
        course.setEnrollments(List.of(enrollmentInactive));

        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));

        CourseDTO result = courseService.getCourseById(5L);

        assertThat(result.getEnrollmentCount()).isEqualTo(0);
        assertThat(result.getCompletedCount()).isEqualTo(0);
    }

    // ── toDTO — null employee handled ────────────────────────────────────────

    @Test
    void toDTO_nullEmployeeEnrollments_excluded_noNullPointerException() {
        course.setEnrollments(List.of(enrollmentNullEmployee, enrollmentActive1));

        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));

        CourseDTO result = courseService.getCourseById(5L);

        // null employee enrollment is filtered, only active emp counted
        assertThat(result.getEnrollmentCount()).isEqualTo(1);
        assertThat(result.getEnrolledEmployeeNames()).contains("Alice Smith");
    }

    @Test
    void toDTO_allNullEmployeeEnrollments_returnsZeroCounts() {
        course.setEnrollments(List.of(enrollmentNullEmployee));

        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));

        CourseDTO result = courseService.getCourseById(5L);

        assertThat(result.getEnrollmentCount()).isEqualTo(0);
        assertThat(result.getCompletedCount()).isEqualTo(0);
        assertThat(result.getInProgressCount()).isEqualTo(0);
        assertThat(result.getEnrolledEmployeeNames()).isEmpty();
    }

    // ── toDTO — empty enrollments ─────────────────────────────────────────────

    @Test
    void toDTO_emptyEnrollments_returnsZeroCounts() {
        course.setEnrollments(List.of());

        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));

        CourseDTO result = courseService.getCourseById(5L);

        assertThat(result.getEnrollmentCount()).isEqualTo(0);
        assertThat(result.getCompletedCount()).isEqualTo(0);
        assertThat(result.getInProgressCount()).isEqualTo(0);
        assertThat(result.getEnrolledEmployeeNames()).isEmpty();
    }

    @Test
    void toDTO_nullEnrollmentsList_returnsZeroCounts() {
        course.setEnrollments(null);

        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));

        CourseDTO result = courseService.getCourseById(5L);

        assertThat(result.getEnrollmentCount()).isEqualTo(0);
    }

    // ── toDTOForTeam — active employees included ──────────────────────────────

    @Test
    void toDTOForTeam_activeTeamMembersIncluded() {
        Set<Long> teamIds = new HashSet<>(Arrays.asList(10L, 11L));
        course.setEnrollments(List.of(enrollmentActive1, enrollmentActive2));

        when(courseRepository.findByActive(true)).thenReturn(List.of(course));
        when(employeeDetailsRepository.findByManagerIdAndActiveTrue(1L))
                .thenReturn(List.of(activeEmp1, activeEmp2));

        List<CourseDTO> results = courseService.getCoursesForManager(1L);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getEnrollmentCount()).isEqualTo(2);
    }

    @Test
    void toDTOForTeam_inactiveTeamMemberExcluded() {
        course.setEnrollments(List.of(enrollmentActive1, enrollmentInactive));

        when(courseRepository.findByActive(true)).thenReturn(List.of(course));
        when(employeeDetailsRepository.findByManagerIdAndActiveTrue(1L))
                .thenReturn(List.of(activeEmp1));

        List<CourseDTO> results = courseService.getCoursesForManager(1L);

        assertThat(results).hasSize(1);
        // Only enrollmentActive1 matches both active+in-team; enrollmentInactive is inactive
        assertThat(results.get(0).getEnrolledEmployeeNames()).contains("Alice Smith");
        assertThat(results.get(0).getEnrolledEmployeeNames()).doesNotContain("Carol White");
    }

    @Test
    void toDTOForTeam_nullEmployeeEnrollment_excluded_noNullPointerException() {
        course.setEnrollments(List.of(enrollmentNullEmployee, enrollmentActive1));

        when(courseRepository.findByActive(true)).thenReturn(List.of(course));
        when(employeeDetailsRepository.findByManagerIdAndActiveTrue(1L))
                .thenReturn(List.of(activeEmp1));

        List<CourseDTO> results = courseService.getCoursesForManager(1L);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getEnrollmentCount()).isEqualTo(1);
    }

    @Test
    void toDTOForTeam_emptyEnrollments_courseFilteredOut() {
        course.setEnrollments(List.of());

        when(courseRepository.findByActive(true)).thenReturn(List.of(course));
        when(employeeDetailsRepository.findByManagerIdAndActiveTrue(1L))
                .thenReturn(List.of(activeEmp1));

        // enrollmentCount == 0, so course is filtered out
        List<CourseDTO> results = courseService.getCoursesForManager(1L);

        assertThat(results).isEmpty();
    }

    @Test
    void toDTOForTeam_noTeamMembers_courseFilteredOut() {
        course.setEnrollments(List.of(enrollmentActive1));

        when(courseRepository.findByActive(true)).thenReturn(List.of(course));
        when(employeeDetailsRepository.findByManagerIdAndActiveTrue(99L))
                .thenReturn(List.of());

        List<CourseDTO> results = courseService.getCoursesForManager(99L);

        // Manager itself (99) is added to teamIds, but its not enrolled so count = 0
        assertThat(results).isEmpty();
    }

    // ── getCourseById — not found ────────────────────────────────────────────

    @Test
    void getCourseById_notFound_throwsResourceNotFoundException() {
        when(courseRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.getCourseById(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getAllCourses ─────────────────────────────────────────────────────────

    @Test
    void getAllCourses_onlyActiveCourses() {
        course.setEnrollments(List.of());
        when(courseRepository.findByActive(true)).thenReturn(List.of(course));

        List<CourseDTO> result = courseService.getAllCourses();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Spring Boot Fundamentals");
    }

    @Test
    void getAllCourses_empty_returnsEmptyList() {
        when(courseRepository.findByActive(true)).thenReturn(List.of());

        List<CourseDTO> result = courseService.getAllCourses();

        assertThat(result).isEmpty();
    }

    // ── enrollEmployee — already enrolled ────────────────────────────────────

    @Test
    void enrollEmployee_alreadyEnrolled_throwsBadRequestException() {
        when(employeeDetailsRepository.findById(10L)).thenReturn(Optional.of(activeEmp1));
        when(enrollmentRepository.existsByEmployeeIdAndCourseId(10L, 5L)).thenReturn(true);

        assertThatThrownBy(() -> courseService.enrollEmployee(5L, 10L))
                .isInstanceOf(com.emp.management.exception.BadRequestException.class)
                .hasMessageContaining("Already enrolled");
    }

    @Test
    void enrollEmployee_notYetEnrolled_savesEnrollment() {
        when(enrollmentRepository.existsByEmployeeIdAndCourseId(10L, 5L)).thenReturn(false);
        when(employeeDetailsRepository.findById(10L)).thenReturn(Optional.of(activeEmp1));
        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.save(any())).thenReturn(enrollmentActive1);
        doNothing().when(courseNotificationService).createNotification(any(), any());

        courseService.enrollEmployee(5L, 10L);

        verify(enrollmentRepository).save(any());
    }
}
