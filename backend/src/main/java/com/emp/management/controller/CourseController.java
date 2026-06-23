package com.emp.management.controller;

import com.emp.management.dto.CourseLearnerDTO;
import com.emp.management.dto.CourseDTO;
import com.emp.management.repository.EmployeeDetailsRepository;
import com.emp.management.service.AIQuizService;
import com.emp.management.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;
    private final AIQuizService aiQuizService;
    private final EmployeeDetailsRepository employeeDetailsRepository;

    @GetMapping
    public ResponseEntity<List<CourseDTO>> getAllCourses() {
        return ResponseEntity.ok(courseService.getAllCourses());
    }

    @GetMapping("/all-enrollments")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public ResponseEntity<List<CourseLearnerDTO>> getAllEnrollments() {
        return ResponseEntity.ok(courseService.getAllEnrollments());
    }

    @GetMapping("/manager/{managerId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'MANAGER', 'ASSISTANT_MANAGER')")
    public ResponseEntity<List<CourseDTO>> getCoursesForManager(@PathVariable Long managerId) {
        return ResponseEntity.ok(courseService.getCoursesForManager(managerId));
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<CourseDTO>> getCoursesForEmployee(
            @PathVariable Long employeeId, Authentication authentication) {
        if (authentication != null) {
            var requester = employeeDetailsRepository.findByUserEmail(authentication.getName()).orElse(null);
            if (requester != null) {
                String role = requester.getUser().getRole().name();
                if (!"ADMIN".equals(role) && !"DIRECTOR".equals(role)) {
                    boolean isSelf = requester.getId().equals(employeeId);
                    var target = employeeDetailsRepository.findById(employeeId).orElse(null);
                    boolean isDirectReport = ("MANAGER".equals(role) || "ASSISTANT_MANAGER".equals(role))
                            && target != null && target.getManager() != null
                            && target.getManager().getId().equals(requester.getId());
                    if (!isSelf && !isDirectReport) {
                        throw new AccessDeniedException("You can only view course data for employees in your reporting hierarchy");
                    }
                }
            }
        }
        return ResponseEntity.ok(courseService.getCoursesForEmployee(employeeId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CourseDTO> getCourse(@PathVariable Long id) {
        return ResponseEntity.ok(courseService.getCourseById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public ResponseEntity<CourseDTO> createCourse(@RequestBody CourseDTO dto,
                                                   @RequestParam Long createdBy) {
        CourseDTO saved = courseService.createCourse(dto, createdBy);
        aiQuizService.generateAndSaveQuiz(saved.getId());
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public ResponseEntity<CourseDTO> updateCourse(@PathVariable Long id,
                                                   @RequestBody CourseDTO dto) {
        return ResponseEntity.ok(courseService.updateCourse(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public ResponseEntity<String> deleteCourse(@PathVariable Long id) {
        courseService.deleteCourse(id);
        return ResponseEntity.ok("Course deleted");
    }

    @PostMapping("/{courseId}/enroll/{employeeId}")
    public ResponseEntity<String> enroll(@PathVariable Long courseId,
                                          @PathVariable Long employeeId) {
        courseService.enrollEmployee(courseId, employeeId);
        return ResponseEntity.ok("Enrolled successfully");
    }

    @PostMapping("/{courseId}/assign")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','MANAGER','ASSISTANT_MANAGER')")
    public ResponseEntity<String> assignCourse(@PathVariable Long courseId,
                                                @RequestBody Map<String, Object> body,
                                                Authentication authentication) {
        Long employeeId = body.get("employeeId") != null
                ? ((Number) body.get("employeeId")).longValue() : null;
        boolean assignAll = Boolean.TRUE.equals(body.get("assignAll"));

        // Resolve assigner display name: prefer full name from EmployeeDetails, fall back to email
        String assignerName = authentication.getName();
        var assignerEmp = employeeDetailsRepository.findByUserEmail(authentication.getName()).orElse(null);
        if (assignerEmp != null) {
            assignerName = assignerEmp.getFullName();
        }

        courseService.assignCourse(courseId, employeeId, assignAll, assignerName);
        return ResponseEntity.ok("Course assigned successfully");
    }

    @PostMapping("/{courseId}/watch/{employeeId}")
    public ResponseEntity<String> markVideoWatched(@PathVariable Long courseId,
                                                    @PathVariable Long employeeId) {
        courseService.markVideoWatched(courseId, employeeId);
        return ResponseEntity.ok("Progress updated");
    }

    @GetMapping("/{courseId}/enrolled-employee-ids")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','MANAGER','ASSISTANT_MANAGER')")
    public ResponseEntity<List<Long>> getEnrolledEmployeeIds(@PathVariable Long courseId) {
        return ResponseEntity.ok(courseService.getEnrolledEmployeeIds(courseId));
    }

    @GetMapping("/{id}/learners")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','MANAGER','ASSISTANT_MANAGER')")
    public ResponseEntity<List<CourseLearnerDTO>> getLearners(@PathVariable Long id) {
        return ResponseEntity.ok(courseService.getLearners(id));
    }

    @GetMapping("/{courseId}/exam-questions")
    public ResponseEntity<List<Map<String, Object>>> getExamQuestions(@PathVariable Long courseId) {
        return ResponseEntity.ok(courseService.getExamQuestions(courseId));
    }

    @GetMapping("/{courseId}/generate-questions")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public ResponseEntity<List<Map<String, Object>>> generateQuestions(@PathVariable Long courseId) {
        return ResponseEntity.ok(aiQuizService.generateQuestionsOnly(courseId));
    }

    @GetMapping("/{courseId}/admin-exam-questions")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public ResponseEntity<List<Map<String, Object>>> getAdminExamQuestions(@PathVariable Long courseId) {
        return ResponseEntity.ok(courseService.getAdminExamQuestions(courseId));
    }

    @PostMapping("/{courseId}/exam/{employeeId}")
    public ResponseEntity<Map<String, Object>> submitExam(@PathVariable Long courseId,
                                                           @PathVariable Long employeeId,
                                                           @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Integer> answers = (List<Integer>) body.get("answers");
        return ResponseEntity.ok(courseService.submitExam(courseId, employeeId, answers));
    }

    @PostMapping("/{courseId}/exam")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public ResponseEntity<String> createExam(@PathVariable Long courseId,
                                              @RequestBody Map<String, Object> body) {
        courseService.createExam(courseId, body.get("questions"),
            ((Number) body.get("passingScore")).intValue());
        return ResponseEntity.ok("Exam created");
    }
}
