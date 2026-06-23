package com.emp.management.service;

import com.emp.management.dto.CourseLearnerDTO;
import com.emp.management.dto.CourseDTO;
import com.emp.management.entity.*;
import com.emp.management.exception.BadRequestException;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CertificateRepository certificateRepository;
    private final ExamRepository examRepository;
    private final EmployeeDetailsRepository employeeDetailsRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final CourseNotificationService courseNotificationService;
    private final EmailService emailService;

    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String frontendBaseUrl;

    @Transactional(readOnly = true)
    public List<CourseLearnerDTO> getLearners(Long courseId) {
        return enrollmentRepository.findByCourseId(courseId).stream().map(e -> {
            String certNo = certificateRepository
                    .findByEmployeeIdAndCourseId(e.getEmployee().getId(), courseId)
                    .map(Certificate::getCertificateNumber)
                    .orElse(null);
            int pct = switch (e.getStatus()) {
                case COMPLETED   -> 100;
                case IN_PROGRESS -> 60;
                default          -> 0;
            };
            return CourseLearnerDTO.builder()
                    .employeeId(e.getEmployee().getId())
                    .employeeName(e.getEmployee().getFirstName() + " " + e.getEmployee().getLastName())
                    .employeeCode(e.getEmployee().getEmployeeCode())
                    .status(e.getStatus().name())
                    .progressPercent(pct)
                    .completionDate(e.getCompletionDate() != null
                            ? e.getCompletionDate().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")) : null)
                    .certificateNumber(certNo)
                    .build();
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<CourseLearnerDTO> getAllEnrollments() {
        return enrollmentRepository.findAll().stream()
            .filter(e -> e.getEmployee() != null && e.getEmployee().isActive()
                    && e.getEmployee().getUser() != null
                    && e.getEmployee().getUser().getRole() != Role.ADMIN
                    && e.getEmployee().getUser().getRole() != Role.DIRECTOR)
            .map(e -> {
                String certNo = certificateRepository
                        .findByEmployeeIdAndCourseId(e.getEmployee().getId(), e.getCourse().getId())
                        .map(Certificate::getCertificateNumber).orElse(null);
                int pct = switch (e.getStatus()) {
                    case COMPLETED   -> 100;
                    case IN_PROGRESS -> 60;
                    default          -> 0;
                };
                return CourseLearnerDTO.builder()
                        .employeeId(e.getEmployee().getId())
                        .employeeName(e.getEmployee().getFirstName() + " " + e.getEmployee().getLastName())
                        .employeeCode(e.getEmployee().getEmployeeCode())
                        .courseId(e.getCourse().getId())
                        .courseTitle(e.getCourse().getTitle())
                        .status(e.getStatus().name())
                        .progressPercent(pct)
                        .completionDate(e.getCompletionDate() != null
                                ? e.getCompletionDate().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")) : null)
                        .certificateNumber(certNo)
                        .examScore(e.getExamScore())
                        .build();
            }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CourseDTO> getAllCourses() {
        return courseRepository.findByActive(true).stream()
                .map(c -> toDTO(c, null)).collect(Collectors.toList());
    }

    public List<CourseDTO> getCoursesForEmployee(Long employeeId) {
        return courseRepository.findEnrolledActiveCoursesForEmployee(employeeId).stream()
                .map(c -> toDTO(c, employeeId)).collect(Collectors.toList());
    }

    public CourseDTO getCourseById(Long id) {
        return toDTO(findCourse(id), null);
    }

    @Transactional
    public CourseDTO createCourse(CourseDTO dto, Long createdByUserId) {
        User creator = userRepository.findById(createdByUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", createdByUserId));

        Course course = Course.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .content(dto.getContent())
                .youtubeUrl(sanitizeUrl(dto.getYoutubeUrl()))
                .durationHours(dto.getDurationHours())
                .createdBy(creator)
                .active(true)
                .build();

        return toDTO(courseRepository.save(course), null);
    }

    @Transactional
    public CourseDTO updateCourse(Long id, CourseDTO dto) {
        Course course = findCourse(id);
        course.setTitle(dto.getTitle());
        course.setDescription(dto.getDescription());
        course.setContent(dto.getContent());
        course.setYoutubeUrl(sanitizeUrl(dto.getYoutubeUrl()));
        course.setDurationHours(dto.getDurationHours());
        return toDTO(courseRepository.save(course), null);
    }

    @Transactional
    public void deleteCourse(Long id) {
        Course course = findCourse(id);
        course.setActive(false);
        courseRepository.save(course);
    }

    @Transactional
    public void enrollEmployee(Long courseId, Long employeeId) {
        EmployeeDetails employee = employeeDetailsRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));
        if (employee.getUser() != null) {
            Role role = employee.getUser().getRole();
            if (role == Role.ADMIN || role == Role.DIRECTOR) {
                throw new BadRequestException("Admin and Director accounts cannot be enrolled in courses");
            }
        }
        if (enrollmentRepository.existsByEmployeeIdAndCourseId(employeeId, courseId)) {
            throw new BadRequestException("Already enrolled in this course");
        }
        Course course = findCourse(courseId);
        enrollmentRepository.save(Enrollment.builder()
                .employee(employee).course(course).status(EnrollmentStatus.ENROLLED).build());
        courseNotificationService.createNotification(employee, course);
    }

    @Transactional
    public void assignCourse(Long courseId, Long employeeId, boolean assignAll, String assignerName) {
        if (!assignAll && employeeId == null) {
            throw new BadRequestException("Employee ID is required when not assigning to all employees");
        }
        Course course = findCourse(courseId);
        List<EmployeeDetails> targets;
        if (assignAll) {
            targets = employeeDetailsRepository.findByActive(true).stream()
                    .filter(e -> e.getUser() == null
                        || (e.getUser().getRole() != Role.ADMIN && e.getUser().getRole() != Role.DIRECTOR))
                    .collect(Collectors.toList());
        } else {
            EmployeeDetails target = employeeDetailsRepository.findById(employeeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));
            if (target.getUser() != null) {
                Role role = target.getUser().getRole();
                if (role == Role.ADMIN || role == Role.DIRECTOR) {
                    throw new BadRequestException("Admin and Director accounts cannot be assigned to courses");
                }
            }
            targets = List.of(target);
        }

        String courseLink = frontendBaseUrl + "/courses";
        LocalDate today   = LocalDate.now();

        for (EmployeeDetails emp : targets) {
            if (!enrollmentRepository.existsByEmployeeIdAndCourseId(emp.getId(), courseId)) {
                enrollmentRepository.save(Enrollment.builder()
                        .employee(emp).course(course).status(EnrollmentStatus.ENROLLED).build());
                courseNotificationService.createNotification(emp, course);

                // Send individual email to each newly enrolled employee
                String empEmail = emp.getUser() != null ? emp.getUser().getEmail() : null;
                if (empEmail != null) {
                    emailService.sendCourseAssignmentEmail(
                            empEmail,
                            emp.getFullName(),
                            course.getTitle(),
                            assignerName,
                            today,
                            courseLink
                    );
                }
            }
        }
    }

    @Transactional
    public void markVideoWatched(Long courseId, Long employeeId) {
        Enrollment enrollment = enrollmentRepository.findByEmployeeIdAndCourseId(employeeId, courseId)
                .orElseThrow(() -> new BadRequestException("Not enrolled in this course"));
        if (enrollment.getStatus() == EnrollmentStatus.ENROLLED) {
            enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
            enrollmentRepository.save(enrollment);
        }
    }

    public List<Long> getEnrolledEmployeeIds(Long courseId) {
        return enrollmentRepository.findByCourseId(courseId).stream()
                .map(e -> e.getEmployee().getId())
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getExamQuestions(Long courseId) {
        Exam exam = examRepository.findByCourseId(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found for course: " + courseId));
        try {
            List<Map<String, Object>> questions = objectMapper.readValue(
                    exam.getQuestions(), new TypeReference<List<Map<String, Object>>>() {});
            // Strip correct answers before sending to client
            questions.forEach(q -> q.remove("correctAnswer"));
            return questions;
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<Map<String, Object>> getAdminExamQuestions(Long courseId) {
        Exam exam = examRepository.findByCourseId(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found for course: " + courseId));
        try {
            return objectMapper.readValue(
                    exam.getQuestions(), new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    @Transactional
    public Map<String, Object> submitExam(Long courseId, Long employeeId, List<Integer> answers) {
        Enrollment enrollment = enrollmentRepository.findByEmployeeIdAndCourseId(employeeId, courseId)
                .orElseThrow(() -> new BadRequestException("Not enrolled in this course"));

        Exam exam = examRepository.findByCourseId(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found for course: " + courseId));

        List<Map<String, Object>> questions;
        try {
            questions = objectMapper.readValue(
                    exam.getQuestions(), new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            questions = new ArrayList<>();
        }

        int correct = 0;
        List<Map<String, Object>> questionResults = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            Map<String, Object> q = new HashMap<>(questions.get(i));
            int userAnswer = (i < answers.size() && answers.get(i) != null) ? answers.get(i) : -1;
            int correctAnswer = ((Number) q.getOrDefault("correctAnswer", -1)).intValue();
            boolean isCorrect = userAnswer == correctAnswer;
            if (isCorrect) correct++;
            q.put("userAnswer", userAnswer);
            q.put("isCorrect", isCorrect);
            questionResults.add(q);
        }
        int score = questions.isEmpty() ? 0 : correct * 100 / questions.size();

        enrollment.setExamScore(score);
        boolean passed = score >= exam.getPassingScore();
        String certificateNumber = null;

        if (passed) {
            enrollment.setStatus(EnrollmentStatus.COMPLETED);
            enrollment.setCompletionDate(LocalDateTime.now());
            if (!certificateRepository.existsByEmployeeIdAndCourseId(employeeId, courseId)) {
                certificateNumber = "CERT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                certificateRepository.save(Certificate.builder()
                        .employee(enrollment.getEmployee())
                        .course(enrollment.getCourse())
                        .certificateNumber(certificateNumber)
                        .score(score)
                        .build());
            } else {
                certificateNumber = certificateRepository
                        .findByEmployeeIdAndCourseId(employeeId, courseId)
                        .map(Certificate::getCertificateNumber).orElse(null);
            }
        } else {
            enrollment.setStatus(EnrollmentStatus.FAILED);
        }
        enrollmentRepository.save(enrollment);

        Map<String, Object> result = new HashMap<>();
        result.put("score", score);
        result.put("passed", passed);
        result.put("passingScore", exam.getPassingScore());
        result.put("enrollmentStatus", passed ? "COMPLETED" : "FAILED");
        result.put("certificateNumber", certificateNumber);
        result.put("questions", questionResults);
        return result;
    }

    @Transactional
    public void createExam(Long courseId, Object questionsData, int passingScore) {
        Course course = findCourse(courseId);
        Exam exam = examRepository.findByCourseId(courseId)
                .orElse(Exam.builder().course(course).build());

        String questionsJson;
        if (questionsData instanceof String s) {
            questionsJson = s;
        } else {
            try { questionsJson = objectMapper.writeValueAsString(questionsData); }
            catch (Exception e) { questionsJson = "[]"; }
        }
        exam.setQuestions(questionsJson);
        exam.setPassingScore(passingScore);
        examRepository.save(exam);
    }

    private int calculateScore(String questionsJson, List<Integer> answers) {
        try {
            List<Map<String, Object>> questions = objectMapper.readValue(
                    questionsJson, new TypeReference<List<Map<String, Object>>>() {});
            if (questions.isEmpty()) return 0;
            int correct = 0;
            for (int i = 0; i < Math.min(answers.size(), questions.size()); i++) {
                int rightAnswer = ((Number) questions.get(i).get("correctAnswer")).intValue();
                if (answers.get(i) != null && answers.get(i) == rightAnswer) correct++;
            }
            return correct * 100 / questions.size();
        } catch (Exception e) {
            return 0;
        }
    }

    private String sanitizeUrl(String url) {
        if (url == null) return null;
        String trimmed = url.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Transactional(readOnly = true)
    public List<CourseDTO> getCoursesForManager(Long managerId) {
        Set<Long> teamIds = employeeDetailsRepository.findByManagerIdAndActiveTrue(managerId)
                .stream().map(EmployeeDetails::getId).collect(Collectors.toSet());
        teamIds.add(managerId);
        return courseRepository.findByActive(true).stream()
                .map(c -> toDTOForTeam(c, teamIds))
                .filter(dto -> dto.getEnrollmentCount() > 0)
                .collect(Collectors.toList());
    }

    private CourseDTO toDTOForTeam(Course c, Set<Long> teamIds) {
        List<com.emp.management.entity.Enrollment> all =
                c.getEnrollments() != null ? c.getEnrollments() : List.of();
        List<com.emp.management.entity.Enrollment> teamEnrollments = all.stream()
                .filter(e -> e.getEmployee() != null && e.getEmployee().isActive()
                        && teamIds.contains(e.getEmployee().getId())
                        && (e.getEmployee().getUser() == null
                            || (e.getEmployee().getUser().getRole() != Role.ADMIN
                                && e.getEmployee().getUser().getRole() != Role.DIRECTOR)))
                .collect(Collectors.toList());

        int completedCount  = (int) teamEnrollments.stream().filter(e -> e.getStatus() == EnrollmentStatus.COMPLETED).count();
        int inProgressCount = (int) teamEnrollments.stream().filter(e -> e.getStatus() == EnrollmentStatus.IN_PROGRESS).count();
        List<String> enrolledNames = teamEnrollments.stream()
                .map(e -> e.getEmployee().getFirstName() + " " + e.getEmployee().getLastName())
                .collect(Collectors.toList());

        return CourseDTO.builder()
                .id(c.getId())
                .title(c.getTitle())
                .description(c.getDescription())
                .content(c.getContent())
                .youtubeUrl(c.getYoutubeUrl())
                .durationHours(c.getDurationHours())
                .active(c.isActive())
                .createdByName(c.getCreatedBy() != null ? c.getCreatedBy().getEmail() : null)
                .createdById(c.getCreatedBy() != null ? c.getCreatedBy().getId() : null)
                .createdAt(c.getCreatedAt())
                .enrollmentCount(teamEnrollments.size())
                .completedCount(completedCount)
                .inProgressCount(inProgressCount)
                .enrolledEmployeeNames(enrolledNames)
                .hasExam(c.getExam() != null)
                .examPassingScore(c.getExam() != null ? c.getExam().getPassingScore() : null)
                .build();
    }

    private Course findCourse(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", id));
    }

    private CourseDTO toDTO(Course c, Long employeeId) {
        String enrollStatus = null;
        Integer examScore = null;
        String certificateNumber = null;

        if (employeeId != null) {
            var enrollment = enrollmentRepository.findByEmployeeIdAndCourseId(employeeId, c.getId());
            enrollStatus = enrollment.map(e -> e.getStatus().name()).orElse("NOT_ENROLLED");
            examScore = enrollment.map(Enrollment::getExamScore).orElse(null);
            if (EnrollmentStatus.COMPLETED.name().equals(enrollStatus)) {
                certificateNumber = certificateRepository
                        .findByEmployeeIdAndCourseId(employeeId, c.getId())
                        .map(Certificate::getCertificateNumber).orElse(null);
            }
        }

        List<com.emp.management.entity.Enrollment> enrollments =
                (c.getEnrollments() != null ? c.getEnrollments() : List.<com.emp.management.entity.Enrollment>of())
                .stream()
                .filter(e -> e.getEmployee() != null && e.getEmployee().isActive()
                        && (e.getEmployee().getUser() == null
                            || (e.getEmployee().getUser().getRole() != Role.ADMIN
                                && e.getEmployee().getUser().getRole() != Role.DIRECTOR)))
                .collect(Collectors.toList());

        int completedCount = (int) enrollments.stream()
                .filter(e -> e.getStatus() == EnrollmentStatus.COMPLETED).count();
        int inProgressCount = (int) enrollments.stream()
                .filter(e -> e.getStatus() == EnrollmentStatus.IN_PROGRESS).count();
        List<String> enrolledNames = enrollments.stream()
                .map(e -> e.getEmployee().getFirstName() + " " + e.getEmployee().getLastName())
                .collect(Collectors.toList());

        return CourseDTO.builder()
                .id(c.getId())
                .title(c.getTitle())
                .description(c.getDescription())
                .content(c.getContent())
                .youtubeUrl(c.getYoutubeUrl())
                .durationHours(c.getDurationHours())
                .active(c.isActive())
                .createdByName(c.getCreatedBy() != null ? c.getCreatedBy().getEmail() : null)
                .createdById(c.getCreatedBy() != null ? c.getCreatedBy().getId() : null)
                .createdAt(c.getCreatedAt())
                .enrollmentCount(enrollments.size())
                .completedCount(completedCount)
                .inProgressCount(inProgressCount)
                .enrolledEmployeeNames(enrolledNames)
                .hasExam(c.getExam() != null)
                .examPassingScore(c.getExam() != null ? c.getExam().getPassingScore() : null)
                .enrollmentStatus(enrollStatus)
                .examScore(examScore)
                .certificateNumber(certificateNumber)
                .build();
    }
}
