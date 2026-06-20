package com.emp.management.service;

import com.emp.management.dto.CourseLearnerDTO;
import com.emp.management.dto.CourseDTO;
import com.emp.management.entity.*;
import com.emp.management.exception.BadRequestException;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock private CourseRepository courseRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private CertificateRepository certificateRepository;
    @Mock private ExamRepository examRepository;
    @Mock private EmployeeDetailsRepository employeeDetailsRepository;
    @Mock private UserRepository userRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private CourseNotificationService courseNotificationService;
    @Mock private EmailService emailService;

    @InjectMocks private CourseService courseService;

    private Course course;
    private EmployeeDetails employee;
    private User empUser;
    private Enrollment enrollment;
    private Exam exam;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(courseService, "frontendBaseUrl", "http://localhost:3000");

        User creator = User.builder().id(10L).email("admin@company.com").role(Role.ADMIN).build();

        empUser = User.builder().id(1L).email("emp@company.com").role(Role.EMPLOYEE).build();
        employee = EmployeeDetails.builder()
                .id(1L).user(empUser).firstName("John").lastName("Doe")
                .employeeCode("EMP10001").active(true).build();
        empUser.setEmployeeDetails(employee);

        course = Course.builder()
                .id(5L).title("Java Fundamentals").description("Learn Java")
                .content("Content here").durationHours(10)
                .youtubeUrl("https://youtube.com/watch?v=abc")
                .active(true).createdBy(creator)
                .enrollments(new ArrayList<>())
                .build();

        enrollment = Enrollment.builder()
                .id(1L).employee(employee).course(course)
                .status(EnrollmentStatus.ENROLLED)
                .build();

        exam = Exam.builder()
                .id(1L).course(course)
                .questions("[{\"question\":\"Q1\",\"options\":[\"A\",\"B\"],\"correctAnswer\":0}]")
                .passingScore(70).totalMarks(100)
                .build();
    }

    // ── getLearners ───────────────────────────────────────────────────────────

    @Test
    void getLearners_enrolled_returnsDTO_withZeroProgress() {
        when(enrollmentRepository.findByCourseId(5L)).thenReturn(List.of(enrollment));
        when(certificateRepository.findByEmployeeIdAndCourseId(1L, 5L)).thenReturn(Optional.empty());

        List<CourseLearnerDTO> result = courseService.getLearners(5L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmployeeName()).isEqualTo("John Doe");
        assertThat(result.get(0).getProgressPercent()).isEqualTo(0);
        assertThat(result.get(0).getCertificateNumber()).isNull();
    }

    @Test
    void getLearners_inProgress_returns60Percent() {
        enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
        when(enrollmentRepository.findByCourseId(5L)).thenReturn(List.of(enrollment));
        when(certificateRepository.findByEmployeeIdAndCourseId(1L, 5L)).thenReturn(Optional.empty());

        List<CourseLearnerDTO> result = courseService.getLearners(5L);

        assertThat(result.get(0).getProgressPercent()).isEqualTo(60);
    }

    @Test
    void getLearners_completed_returns100PercentWithCertificate() {
        enrollment.setStatus(EnrollmentStatus.COMPLETED);
        enrollment.setCompletionDate(LocalDateTime.now());
        Certificate cert = Certificate.builder().certificateNumber("CERT-ABC123").build();
        when(enrollmentRepository.findByCourseId(5L)).thenReturn(List.of(enrollment));
        when(certificateRepository.findByEmployeeIdAndCourseId(1L, 5L)).thenReturn(Optional.of(cert));

        List<CourseLearnerDTO> result = courseService.getLearners(5L);

        assertThat(result.get(0).getProgressPercent()).isEqualTo(100);
        assertThat(result.get(0).getCertificateNumber()).isEqualTo("CERT-ABC123");
    }

    @Test
    void getLearners_empty_returnsEmptyList() {
        when(enrollmentRepository.findByCourseId(5L)).thenReturn(List.of());

        List<CourseLearnerDTO> result = courseService.getLearners(5L);

        assertThat(result).isEmpty();
    }

    // ── getAllEnrollments ─────────────────────────────────────────────────────

    @Test
    void getAllEnrollments_filtersAdminRoles() {
        User adminUser = User.builder().id(2L).email("admin@co.com").role(Role.ADMIN).build();
        EmployeeDetails adminEmp = EmployeeDetails.builder()
                .id(2L).user(adminUser).active(true).build();
        adminUser.setEmployeeDetails(adminEmp);
        Enrollment adminEnrollment = Enrollment.builder()
                .employee(adminEmp).course(course).status(EnrollmentStatus.ENROLLED).build();

        when(enrollmentRepository.findAll()).thenReturn(List.of(enrollment, adminEnrollment));
        when(certificateRepository.findByEmployeeIdAndCourseId(anyLong(), anyLong()))
                .thenReturn(Optional.empty());

        List<CourseLearnerDTO> result = courseService.getAllEnrollments();

        // Only employee enrollment should be returned (admin filtered out)
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmployeeName()).isEqualTo("John Doe");
    }

    @Test
    void getAllEnrollments_filtersInactiveEmployees() {
        EmployeeDetails inactive = EmployeeDetails.builder()
                .id(3L).user(empUser).active(false).firstName("Old").lastName("Emp").build();
        Enrollment inactiveEnrollment = Enrollment.builder()
                .employee(inactive).course(course).status(EnrollmentStatus.ENROLLED).build();
        when(enrollmentRepository.findAll()).thenReturn(List.of(inactiveEnrollment));

        List<CourseLearnerDTO> result = courseService.getAllEnrollments();

        assertThat(result).isEmpty();
    }

    // ── getAllCourses ─────────────────────────────────────────────────────────

    @Test
    void getAllCourses_returnsMappedDTOs() {
        when(courseRepository.findByActive(true)).thenReturn(List.of(course));

        List<CourseDTO> result = courseService.getAllCourses();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Java Fundamentals");
        assertThat(result.get(0).isHasExam()).isFalse(); // no exam attached
    }

    @Test
    void getAllCourses_courseWithExam_hasExamTrue() {
        course.setExam(exam);
        when(courseRepository.findByActive(true)).thenReturn(List.of(course));

        List<CourseDTO> result = courseService.getAllCourses();

        assertThat(result.get(0).isHasExam()).isTrue();
        assertThat(result.get(0).getExamPassingScore()).isEqualTo(70);
    }

    // ── getCourseById ─────────────────────────────────────────────────────────

    @Test
    void getCourseById_found_returnsDTO() {
        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));

        CourseDTO result = courseService.getCourseById(5L);

        assertThat(result.getId()).isEqualTo(5L);
        assertThat(result.getTitle()).isEqualTo("Java Fundamentals");
    }

    @Test
    void getCourseById_notFound_throwsResourceNotFoundException() {
        when(courseRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.getCourseById(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getCoursesForEmployee ─────────────────────────────────────────────────

    @Test
    void getCoursesForEmployee_returnsEnrolledCourses() {
        when(courseRepository.findEnrolledActiveCoursesForEmployee(1L)).thenReturn(List.of(course));
        when(enrollmentRepository.findByEmployeeIdAndCourseId(1L, 5L)).thenReturn(Optional.of(enrollment));

        List<CourseDTO> result = courseService.getCoursesForEmployee(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEnrollmentStatus()).isEqualTo("ENROLLED");
    }

    @Test
    void getCoursesForEmployee_completedCourse_includesCertificate() {
        enrollment.setStatus(EnrollmentStatus.COMPLETED);
        Certificate cert = Certificate.builder().certificateNumber("CERT-XYZ").build();
        when(courseRepository.findEnrolledActiveCoursesForEmployee(1L)).thenReturn(List.of(course));
        when(enrollmentRepository.findByEmployeeIdAndCourseId(1L, 5L)).thenReturn(Optional.of(enrollment));
        when(certificateRepository.findByEmployeeIdAndCourseId(1L, 5L)).thenReturn(Optional.of(cert));

        List<CourseDTO> result = courseService.getCoursesForEmployee(1L);

        assertThat(result.get(0).getCertificateNumber()).isEqualTo("CERT-XYZ");
    }

    // ── createCourse ──────────────────────────────────────────────────────────

    @Test
    void createCourse_validInput_savesCourse() {
        User creator = User.builder().id(10L).email("admin@co.com").build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(creator));
        when(courseRepository.save(any(Course.class))).thenReturn(course);

        CourseDTO dto = CourseDTO.builder()
                .title("Java Fundamentals").description("Learn Java")
                .youtubeUrl("  https://youtube.com/abc  ") // whitespace trimmed
                .durationHours(10).build();

        CourseDTO result = courseService.createCourse(dto, 10L);

        assertThat(result.getTitle()).isEqualTo("Java Fundamentals");
        verify(courseRepository).save(argThat(c -> c.getTitle().equals("Java Fundamentals")));
    }

    @Test
    void createCourse_blankYoutubeUrl_setsNull() {
        User creator = User.builder().id(10L).email("admin@co.com").build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(creator));
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

        CourseDTO dto = CourseDTO.builder()
                .title("Java").description("desc").youtubeUrl("   ").build();

        courseService.createCourse(dto, 10L);

        verify(courseRepository).save(argThat(c -> c.getYoutubeUrl() == null));
    }

    @Test
    void createCourse_userNotFound_throwsResourceNotFoundException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        CourseDTO dto = CourseDTO.builder().title("Course").build();

        assertThatThrownBy(() -> courseService.createCourse(dto, 999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── updateCourse ──────────────────────────────────────────────────────────

    @Test
    void updateCourse_found_updatesFields() {
        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));
        when(courseRepository.save(any())).thenReturn(course);

        CourseDTO dto = CourseDTO.builder()
                .title("Updated Title").description("New desc")
                .youtubeUrl(null).durationHours(20).build();

        CourseDTO result = courseService.updateCourse(5L, dto);

        verify(courseRepository).save(argThat(c -> c.getTitle().equals("Updated Title") && c.getDurationHours() == 20));
    }

    @Test
    void updateCourse_notFound_throws() {
        when(courseRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.updateCourse(999L, CourseDTO.builder().title("T").build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── deleteCourse ──────────────────────────────────────────────────────────

    @Test
    void deleteCourse_found_setsActiveFalse() {
        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));
        when(courseRepository.save(any())).thenReturn(course);

        courseService.deleteCourse(5L);

        verify(courseRepository).save(argThat(c -> !c.isActive()));
    }

    @Test
    void deleteCourse_notFound_throws() {
        when(courseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.deleteCourse(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── enrollEmployee ────────────────────────────────────────────────────────

    @Test
    void enrollEmployee_validEmployee_savesEnrollmentAndNotification() {
        when(employeeDetailsRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(enrollmentRepository.existsByEmployeeIdAndCourseId(1L, 5L)).thenReturn(false);
        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.save(any())).thenReturn(enrollment);

        courseService.enrollEmployee(5L, 1L);

        verify(enrollmentRepository).save(any(Enrollment.class));
        verify(courseNotificationService).createNotification(employee, course);
    }

    @Test
    void enrollEmployee_adminEmployee_throwsBadRequest() {
        User adminUser = User.builder().id(5L).email("a@co.com").role(Role.ADMIN).build();
        EmployeeDetails adminEmp = EmployeeDetails.builder().id(5L).user(adminUser).active(true).build();
        when(employeeDetailsRepository.findById(5L)).thenReturn(Optional.of(adminEmp));

        assertThatThrownBy(() -> courseService.enrollEmployee(5L, 5L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cannot be enrolled");
    }

    @Test
    void enrollEmployee_alreadyEnrolled_throwsBadRequest() {
        when(employeeDetailsRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(enrollmentRepository.existsByEmployeeIdAndCourseId(1L, 5L)).thenReturn(true);

        assertThatThrownBy(() -> courseService.enrollEmployee(5L, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Already enrolled");
    }

    @Test
    void enrollEmployee_employeeNotFound_throws() {
        when(employeeDetailsRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.enrollEmployee(5L, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── assignCourse ──────────────────────────────────────────────────────────

    @Test
    void assignCourse_toSingleEmployee_enrollsAndSendsEmail() {
        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));
        when(employeeDetailsRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(enrollmentRepository.existsByEmployeeIdAndCourseId(1L, 5L)).thenReturn(false);
        when(enrollmentRepository.save(any())).thenReturn(enrollment);

        courseService.assignCourse(5L, 1L, false, "Admin User");

        verify(enrollmentRepository).save(any(Enrollment.class));
        verify(emailService).sendCourseAssignmentEmail(
                eq("emp@company.com"), eq("John Doe"), eq("Java Fundamentals"),
                eq("Admin User"), any(), eq("http://localhost:3000/courses"));
    }

    @Test
    void assignCourse_toAllEmployees_enrollsAll() {
        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));
        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(employee));
        when(enrollmentRepository.existsByEmployeeIdAndCourseId(1L, 5L)).thenReturn(false);
        when(enrollmentRepository.save(any())).thenReturn(enrollment);

        courseService.assignCourse(5L, null, true, "Admin");

        verify(enrollmentRepository).save(any(Enrollment.class));
    }

    @Test
    void assignCourse_toAllEmployees_skipsAdminRole() {
        User adminUser = User.builder().id(2L).email("a@co.com").role(Role.ADMIN).build();
        EmployeeDetails adminEmp = EmployeeDetails.builder().id(2L).user(adminUser).active(true).build();
        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));
        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(adminEmp));

        courseService.assignCourse(5L, null, true, "Admin");

        // Admin filtered out — no enrollment saved
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void assignCourse_toAllEmployees_skipsAlreadyEnrolled() {
        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));
        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(employee));
        when(enrollmentRepository.existsByEmployeeIdAndCourseId(1L, 5L)).thenReturn(true);

        courseService.assignCourse(5L, null, true, "Admin");

        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void assignCourse_noEmployeeIdAndNotAssignAll_throwsBadRequest() {
        assertThatThrownBy(() -> courseService.assignCourse(5L, null, false, "Admin"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Employee ID is required");
    }

    @Test
    void assignCourse_adminEmployee_throwsBadRequest() {
        User adminUser = User.builder().id(5L).email("a@co.com").role(Role.ADMIN).build();
        EmployeeDetails adminEmp = EmployeeDetails.builder().id(5L).user(adminUser).active(true).build();
        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));
        when(employeeDetailsRepository.findById(5L)).thenReturn(Optional.of(adminEmp));

        assertThatThrownBy(() -> courseService.assignCourse(5L, 5L, false, "Admin"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cannot be assigned");
    }

    // ── markVideoWatched ──────────────────────────────────────────────────────

    @Test
    void markVideoWatched_enrolled_setsInProgress() {
        when(enrollmentRepository.findByEmployeeIdAndCourseId(1L, 5L)).thenReturn(Optional.of(enrollment));
        when(enrollmentRepository.save(any())).thenReturn(enrollment);

        courseService.markVideoWatched(5L, 1L);

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.IN_PROGRESS);
    }

    @Test
    void markVideoWatched_alreadyInProgress_doesNotDowngrade() {
        enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
        when(enrollmentRepository.findByEmployeeIdAndCourseId(1L, 5L)).thenReturn(Optional.of(enrollment));

        courseService.markVideoWatched(5L, 1L);

        // Status stays IN_PROGRESS, no redundant save
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void markVideoWatched_notEnrolled_throwsBadRequest() {
        when(enrollmentRepository.findByEmployeeIdAndCourseId(1L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.markVideoWatched(5L, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Not enrolled");
    }

    // ── getEnrolledEmployeeIds ────────────────────────────────────────────────

    @Test
    void getEnrolledEmployeeIds_returnsList() {
        when(enrollmentRepository.findByCourseId(5L)).thenReturn(List.of(enrollment));

        List<Long> ids = courseService.getEnrolledEmployeeIds(5L);

        assertThat(ids).containsExactly(1L);
    }

    // ── getExamQuestions ──────────────────────────────────────────────────────

    @Test
    void getExamQuestions_found_stripsCorrectAnswer() throws Exception {
        when(examRepository.findByCourseId(5L)).thenReturn(Optional.of(exam));
        List<Map<String, Object>> questions = new ArrayList<>();
        Map<String, Object> q = new HashMap<>();
        q.put("question", "Q1");
        q.put("correctAnswer", 0);
        questions.add(q);
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(questions);

        List<Map<String, Object>> result = courseService.getExamQuestions(5L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).doesNotContainKey("correctAnswer");
    }

    @Test
    void getExamQuestions_examNotFound_throws() {
        when(examRepository.findByCourseId(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.getExamQuestions(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getExamQuestions_parseError_returnsEmptyList() throws Exception {
        when(examRepository.findByCourseId(5L)).thenReturn(Optional.of(exam));
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenThrow(new RuntimeException("JSON parse error"));

        List<Map<String, Object>> result = courseService.getExamQuestions(5L);

        assertThat(result).isEmpty();
    }

    // ── getAdminExamQuestions ─────────────────────────────────────────────────

    @Test
    void getAdminExamQuestions_found_returnsQuestionsWithCorrectAnswer() throws Exception {
        when(examRepository.findByCourseId(5L)).thenReturn(Optional.of(exam));
        List<Map<String, Object>> questions = List.of(Map.of("question", "Q1", "correctAnswer", 0));
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(questions);

        List<Map<String, Object>> result = courseService.getAdminExamQuestions(5L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsKey("correctAnswer");
    }

    @Test
    void getAdminExamQuestions_parseError_returnsEmptyList() throws Exception {
        when(examRepository.findByCourseId(5L)).thenReturn(Optional.of(exam));
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenThrow(new RuntimeException("parse error"));

        List<Map<String, Object>> result = courseService.getAdminExamQuestions(5L);

        assertThat(result).isEmpty();
    }

    // ── submitExam ────────────────────────────────────────────────────────────

    @Test
    void submitExam_allCorrect_passes_createsCertificate() throws Exception {
        when(enrollmentRepository.findByEmployeeIdAndCourseId(1L, 5L)).thenReturn(Optional.of(enrollment));
        when(examRepository.findByCourseId(5L)).thenReturn(Optional.of(exam));
        List<Map<String, Object>> questions = new ArrayList<>();
        Map<String, Object> q = new HashMap<>();
        q.put("question", "Q1"); q.put("correctAnswer", 0);
        questions.add(q);
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(questions);
        when(certificateRepository.existsByEmployeeIdAndCourseId(1L, 5L)).thenReturn(false);
        when(certificateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(enrollmentRepository.save(any())).thenReturn(enrollment);

        Map<String, Object> result = courseService.submitExam(5L, 1L, List.of(0));

        assertThat(result.get("score")).isEqualTo(100);
        assertThat(result.get("passed")).isEqualTo(true);
        assertThat(result.get("certificateNumber")).isNotNull();
        assertThat((String) result.get("enrollmentStatus")).isEqualTo("COMPLETED");
    }

    @Test
    void submitExam_allWrong_fails() throws Exception {
        when(enrollmentRepository.findByEmployeeIdAndCourseId(1L, 5L)).thenReturn(Optional.of(enrollment));
        when(examRepository.findByCourseId(5L)).thenReturn(Optional.of(exam));
        List<Map<String, Object>> questions = new ArrayList<>();
        Map<String, Object> q = new HashMap<>();
        q.put("question", "Q1"); q.put("correctAnswer", 0);
        questions.add(q);
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(questions);
        when(enrollmentRepository.save(any())).thenReturn(enrollment);

        Map<String, Object> result = courseService.submitExam(5L, 1L, List.of(1)); // wrong answer

        assertThat(result.get("score")).isEqualTo(0);
        assertThat(result.get("passed")).isEqualTo(false);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.FAILED);
    }

    @Test
    void submitExam_notEnrolled_throwsBadRequest() {
        when(enrollmentRepository.findByEmployeeIdAndCourseId(99L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.submitExam(5L, 99L, List.of(0)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void submitExam_examNotFound_throws() {
        when(enrollmentRepository.findByEmployeeIdAndCourseId(1L, 5L)).thenReturn(Optional.of(enrollment));
        when(examRepository.findByCourseId(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.submitExam(5L, 1L, List.of(0)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void submitExam_passed_existingCertificate_reusesExistingNumber() throws Exception {
        when(enrollmentRepository.findByEmployeeIdAndCourseId(1L, 5L)).thenReturn(Optional.of(enrollment));
        when(examRepository.findByCourseId(5L)).thenReturn(Optional.of(exam));
        List<Map<String, Object>> questions = new ArrayList<>();
        Map<String, Object> q = new HashMap<>();
        q.put("question", "Q1"); q.put("correctAnswer", 0);
        questions.add(q);
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(questions);
        when(certificateRepository.existsByEmployeeIdAndCourseId(1L, 5L)).thenReturn(true);
        Certificate existingCert = Certificate.builder().certificateNumber("CERT-EXISTING").build();
        when(certificateRepository.findByEmployeeIdAndCourseId(1L, 5L)).thenReturn(Optional.of(existingCert));
        when(enrollmentRepository.save(any())).thenReturn(enrollment);

        Map<String, Object> result = courseService.submitExam(5L, 1L, List.of(0));

        assertThat(result.get("certificateNumber")).isEqualTo("CERT-EXISTING");
    }

    @Test
    void submitExam_emptyQuestions_scoreIsZero() throws Exception {
        when(enrollmentRepository.findByEmployeeIdAndCourseId(1L, 5L)).thenReturn(Optional.of(enrollment));
        when(examRepository.findByCourseId(5L)).thenReturn(Optional.of(exam));
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(new ArrayList<>());
        when(enrollmentRepository.save(any())).thenReturn(enrollment);

        Map<String, Object> result = courseService.submitExam(5L, 1L, List.of());

        assertThat(result.get("score")).isEqualTo(0);
    }

    // ── createExam ────────────────────────────────────────────────────────────

    @Test
    void createExam_newExam_savesWithStringQuestions() throws Exception {
        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));
        when(examRepository.findByCourseId(5L)).thenReturn(Optional.empty());
        when(examRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        courseService.createExam(5L, "[{\"question\":\"Q1\"}]", 70);

        verify(examRepository).save(argThat(e -> e.getPassingScore() == 70));
    }

    @Test
    void createExam_objectQuestions_serializes() throws Exception {
        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));
        when(examRepository.findByCourseId(5L)).thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(any())).thenReturn("[{\"q\":\"Q1\"}]");
        when(examRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Map<String, Object>> questionsObj = List.of(Map.of("q", "Q1"));
        courseService.createExam(5L, questionsObj, 80);

        verify(examRepository).save(argThat(e -> e.getPassingScore() == 80));
    }

    @Test
    void createExam_existingExam_updates() throws Exception {
        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));
        when(examRepository.findByCourseId(5L)).thenReturn(Optional.of(exam));
        when(examRepository.save(any())).thenReturn(exam);

        courseService.createExam(5L, "[{\"question\":\"Q2\"}]", 60);

        verify(examRepository).save(argThat(e -> e.getPassingScore() == 60));
    }

    @Test
    void createExam_courseNotFound_throws() {
        when(courseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.createExam(99L, "[]", 70))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getCoursesForManager ──────────────────────────────────────────────────

    @Test
    void getCoursesForManager_returnsCoursesWithTeamEnrollments() {
        // employee (id=1) is on the manager's team
        Enrollment teamEnrollment = Enrollment.builder()
                .employee(employee).course(course).status(EnrollmentStatus.IN_PROGRESS).build();
        course.setEnrollments(List.of(teamEnrollment));

        when(employeeDetailsRepository.findByManagerIdAndActiveTrue(10L)).thenReturn(List.of(employee));
        when(courseRepository.findByActive(true)).thenReturn(List.of(course));

        List<CourseDTO> result = courseService.getCoursesForManager(10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEnrollmentCount()).isEqualTo(1);
        assertThat(result.get(0).getInProgressCount()).isEqualTo(1);
    }

    @Test
    void getCoursesForManager_noTeamEnrollments_filtersOut() {
        course.setEnrollments(new ArrayList<>()); // no team enrollments
        when(employeeDetailsRepository.findByManagerIdAndActiveTrue(10L)).thenReturn(List.of(employee));
        when(courseRepository.findByActive(true)).thenReturn(List.of(course));

        List<CourseDTO> result = courseService.getCoursesForManager(10L);

        // Filtered out because enrollmentCount == 0
        assertThat(result).isEmpty();
    }
}
