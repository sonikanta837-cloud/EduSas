package com.emp.management.service;

import com.emp.management.dto.*;
import com.emp.management.entity.*;
import com.emp.management.exception.BadRequestException;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PipServiceTest {

    @Mock private PipRepository              pipRepository;
    @Mock private PipGoalRepository          goalRepository;
    @Mock private PipWeeklyReviewRepository  reviewRepository;
    @Mock private PipCommentRepository       commentRepository;
    @Mock private PipAuditLogRepository      auditRepository;
    @Mock private EmployeeDetailsRepository  employeeDetailsRepository;
    @Mock private UserRepository             userRepository;
    @Mock private EmailService               emailService;
    @Mock private PipNotificationService     pipNotificationService;

    @InjectMocks private PipService pipService;

    private EmployeeDetails adminEmp;
    private EmployeeDetails managerEmp;
    private EmployeeDetails regularEmp;
    private PerformanceImprovementPlan samplePip;

    @BeforeEach
    void setUp() {
        User adminUser = User.builder().id(1L).email("admin@company.com").role(Role.ADMIN).active(true).build();
        adminEmp = EmployeeDetails.builder().id(1L).user(adminUser)
                .firstName("Admin").lastName("User").active(true).build();
        adminUser.setEmployeeDetails(adminEmp);

        User mgrUser = User.builder().id(2L).email("mgr@company.com").role(Role.MANAGER).active(true).build();
        managerEmp = EmployeeDetails.builder().id(2L).user(mgrUser)
                .firstName("Jane").lastName("Manager").active(true).build();
        mgrUser.setEmployeeDetails(managerEmp);

        User empUser = User.builder().id(3L).email("emp@company.com").role(Role.EMPLOYEE).active(true).build();
        regularEmp = EmployeeDetails.builder().id(3L).user(empUser)
                .firstName("John").lastName("Doe").manager(managerEmp).active(true).build();
        empUser.setEmployeeDetails(regularEmp);

        samplePip = PerformanceImprovementPlan.builder()
                .id(10L)
                .employee(regularEmp)
                .createdBy(managerEmp)
                .title("Performance Improvement")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(3))
                .status(PipStatus.ACTIVE)
                .reason("Performance below expectations")
                .build();
        // Initialize collections (Lombok @Builder.Default requires explicit init in tests)
    }

    // ── stub helper ──────────────────────────────────────────────────────────

    private void stubPipCountQueries(Long pipId) {
        when(pipRepository.countTotalGoals(pipId)).thenReturn(3L);
        when(pipRepository.countAchievedGoals(pipId)).thenReturn(1L);
        when(pipRepository.countWeeklyReviews(pipId)).thenReturn(2L);
        when(pipRepository.countComments(pipId)).thenReturn(5L);
        when(pipRepository.avgGoalProgress(pipId)).thenReturn(40.0);
    }

    // ── getPips ───────────────────────────────────────────────────────────────

    @Test
    void getPips_asAdmin_returnsAllPips() {
        when(employeeDetailsRepository.findByUserEmail("admin@company.com"))
                .thenReturn(Optional.of(adminEmp));
        when(pipRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(samplePip));
        stubPipCountQueries(10L);

        List<PipDTO> result = pipService.getPips("admin@company.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Performance Improvement");
        verify(pipRepository).findAllByOrderByCreatedAtDesc();
    }

    @Test
    void getPips_asHr_returnsAllPips() {
        User hrUser = User.builder().id(5L).email("hr@company.com").role(Role.HR).active(true).build();
        EmployeeDetails hrEmp = EmployeeDetails.builder().id(5L).user(hrUser)
                .firstName("HR").lastName("Person").active(true).build();
        hrUser.setEmployeeDetails(hrEmp);

        when(employeeDetailsRepository.findByUserEmail("hr@company.com")).thenReturn(Optional.of(hrEmp));
        when(pipRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(samplePip));
        stubPipCountQueries(10L);

        List<PipDTO> result = pipService.getPips("hr@company.com");

        assertThat(result).hasSize(1);
        verify(pipRepository).findAllByOrderByCreatedAtDesc();
    }

    @Test
    void getPips_asManager_returnsOwnAndSubordinatePips() {
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com"))
                .thenReturn(Optional.of(managerEmp));
        when(pipRepository.findByEmployeeIdOrderByCreatedAtDesc(2L)).thenReturn(List.of());
        when(pipRepository.findByEmployeeManagerId(2L)).thenReturn(List.of(samplePip));
        when(pipRepository.findByCreatedByIdOrderByCreatedAtDesc(2L)).thenReturn(List.of());
        stubPipCountQueries(10L);

        List<PipDTO> result = pipService.getPips("mgr@company.com");

        assertThat(result).hasSize(1);
        verify(pipRepository).findByEmployeeManagerId(2L);
    }

    @Test
    void getPips_asEmployee_returnsOnlyOwnPips() {
        when(employeeDetailsRepository.findByUserEmail("emp@company.com"))
                .thenReturn(Optional.of(regularEmp));
        when(pipRepository.findByEmployeeIdOrderByCreatedAtDesc(3L)).thenReturn(List.of(samplePip));
        stubPipCountQueries(10L);

        List<PipDTO> result = pipService.getPips("emp@company.com");

        assertThat(result).hasSize(1);
        verify(pipRepository).findByEmployeeIdOrderByCreatedAtDesc(3L);
    }

    @Test
    void getPips_userNotFound_throwsResourceNotFoundException() {
        when(employeeDetailsRepository.findByUserEmail("ghost@company.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> pipService.getPips("ghost@company.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Employee not found");
    }

    @Test
    void getPips_managerDeduplicatesPips() {
        // Same PIP appears in both own list and subordinate list
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com"))
                .thenReturn(Optional.of(managerEmp));
        when(pipRepository.findByEmployeeIdOrderByCreatedAtDesc(2L)).thenReturn(List.of(samplePip));
        when(pipRepository.findByEmployeeManagerId(2L)).thenReturn(List.of(samplePip)); // duplicate
        when(pipRepository.findByCreatedByIdOrderByCreatedAtDesc(2L)).thenReturn(List.of(samplePip)); // duplicate
        stubPipCountQueries(10L);

        List<PipDTO> result = pipService.getPips("mgr@company.com");

        // Should deduplicate — only 1 unique PIP
        assertThat(result).hasSize(1);
    }

    // ── getPipsByEmployee ─────────────────────────────────────────────────────

    @Test
    void getPipsByEmployee_asAdmin_returnsEmployeePips() {
        when(employeeDetailsRepository.findByUserEmail("admin@company.com"))
                .thenReturn(Optional.of(adminEmp));
        when(pipRepository.findByEmployeeIdOrderByCreatedAtDesc(3L)).thenReturn(List.of(samplePip));
        stubPipCountQueries(10L);

        List<PipDTO> result = pipService.getPipsByEmployee(3L, "admin@company.com");

        assertThat(result).hasSize(1);
    }

    @Test
    void getPipsByEmployee_asEmployeeAccessingOwnPips_succeeds() {
        when(employeeDetailsRepository.findByUserEmail("emp@company.com"))
                .thenReturn(Optional.of(regularEmp));
        when(pipRepository.findByEmployeeIdOrderByCreatedAtDesc(3L)).thenReturn(List.of(samplePip));
        stubPipCountQueries(10L);

        List<PipDTO> result = pipService.getPipsByEmployee(3L, "emp@company.com");

        assertThat(result).hasSize(1);
    }

    @Test
    void getPipsByEmployee_asEmployeeAccessingOthersPips_throwsBadRequest() {
        when(employeeDetailsRepository.findByUserEmail("emp@company.com"))
                .thenReturn(Optional.of(regularEmp));

        // regularEmp.id = 3, trying to access employee 99
        assertThatThrownBy(() -> pipService.getPipsByEmployee(99L, "emp@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Access denied");
    }

    // ── getPipDetail ──────────────────────────────────────────────────────────

    @Test
    void getPipDetail_asAdmin_returnsPipDetail() {
        when(pipRepository.findById(10L)).thenReturn(Optional.of(samplePip));
        when(employeeDetailsRepository.findByUserEmail("admin@company.com"))
                .thenReturn(Optional.of(adminEmp));
        stubPipCountQueries(10L);

        PipDTO result = pipService.getPipDetail(10L, "admin@company.com");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(10L);
    }

    @Test
    void getPipDetail_asEmployeeViewingOwnPip_succeeds() {
        when(pipRepository.findById(10L)).thenReturn(Optional.of(samplePip));
        when(employeeDetailsRepository.findByUserEmail("emp@company.com"))
                .thenReturn(Optional.of(regularEmp));
        stubPipCountQueries(10L);

        // samplePip.employee = regularEmp (id=3), regularEmp.id = 3 — should succeed
        PipDTO result = pipService.getPipDetail(10L, "emp@company.com");

        assertThat(result).isNotNull();
    }

    @Test
    void getPipDetail_asEmployeeViewingOthersPip_throwsBadRequest() {
        // Create a PIP for a different employee
        User otherUser = User.builder().id(4L).email("other@company.com").role(Role.EMPLOYEE).active(true).build();
        EmployeeDetails otherEmp = EmployeeDetails.builder().id(4L).user(otherUser)
                .firstName("Other").lastName("Person").active(true).build();
        otherUser.setEmployeeDetails(otherEmp);

        PerformanceImprovementPlan otherPip = PerformanceImprovementPlan.builder()
                .id(20L)
                .employee(otherEmp)   // belongs to otherEmp, NOT regularEmp
                .createdBy(managerEmp)
                .title("Other PIP")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(3))
                .status(PipStatus.ACTIVE)
                .build();

        when(pipRepository.findById(20L)).thenReturn(Optional.of(otherPip));
        when(employeeDetailsRepository.findByUserEmail("emp@company.com"))
                .thenReturn(Optional.of(regularEmp));

        assertThatThrownBy(() -> pipService.getPipDetail(20L, "emp@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void getPipDetail_pipNotFound_throwsResourceNotFoundException() {
        when(pipRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pipService.getPipDetail(999L, "admin@company.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("PIP not found");
    }

    // ── createPip ─────────────────────────────────────────────────────────────

    @Test
    void createPip_validInput_createsPip() {
        PipDTO dto = PipDTO.builder()
                .employeeId(3L)
                .title("New PIP")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(3))
                .reason("Below target")
                .build();

        when(employeeDetailsRepository.findByUserEmail("mgr@company.com"))
                .thenReturn(Optional.of(managerEmp));
        when(employeeDetailsRepository.findById(3L)).thenReturn(Optional.of(regularEmp));
        when(pipRepository.save(any())).thenReturn(samplePip);
        when(pipRepository.findById(10L)).thenReturn(Optional.of(samplePip));
        stubPipCountQueries(10L);
        when(auditRepository.save(any())).thenReturn(PipAuditLog.builder().build());
        when(userRepository.findByRoleIn(any())).thenReturn(List.of());

        PipDTO result = pipService.createPip(dto, "mgr@company.com");

        assertThat(result).isNotNull();
        verify(pipRepository).save(any(PerformanceImprovementPlan.class));
    }

    @Test
    void createPip_withGoals_savesGoals() {
        PipGoalDTO goalDTO = PipGoalDTO.builder()
                .title("Improve communication")
                .description("Weekly team meetings")
                .targetDate(LocalDate.now().plusWeeks(4))
                .build();

        PipDTO dto = PipDTO.builder()
                .employeeId(3L)
                .title("PIP with Goals")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(3))
                .goals(List.of(goalDTO))
                .build();

        when(employeeDetailsRepository.findByUserEmail("admin@company.com"))
                .thenReturn(Optional.of(adminEmp));
        when(employeeDetailsRepository.findById(3L)).thenReturn(Optional.of(regularEmp));
        when(pipRepository.save(any())).thenReturn(samplePip);
        when(pipRepository.findById(10L)).thenReturn(Optional.of(samplePip));
        when(goalRepository.save(any())).thenReturn(PipGoal.builder()
                .id(1L).pip(samplePip).title("Improve communication")
                .progressPercent(0).status(PipGoalStatus.NOT_STARTED).build());
        stubPipCountQueries(10L);
        when(auditRepository.save(any())).thenReturn(PipAuditLog.builder().build());
        when(userRepository.findByRoleIn(any())).thenReturn(List.of());

        PipDTO result = pipService.createPip(dto, "admin@company.com");

        assertThat(result).isNotNull();
        verify(goalRepository).save(any(PipGoal.class));
    }

    @Test
    void createPip_nullStartDate_throwsBadRequest() {
        PipDTO dto = PipDTO.builder()
                .employeeId(3L)
                .title("PIP")
                .startDate(null)
                .endDate(LocalDate.now().plusMonths(3))
                .build();

        when(employeeDetailsRepository.findByUserEmail("mgr@company.com"))
                .thenReturn(Optional.of(managerEmp));
        when(employeeDetailsRepository.findById(3L)).thenReturn(Optional.of(regularEmp));

        assertThatThrownBy(() -> pipService.createPip(dto, "mgr@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Start date and end date are required");
    }

    @Test
    void createPip_nullEndDate_throwsBadRequest() {
        PipDTO dto = PipDTO.builder()
                .employeeId(3L)
                .title("PIP")
                .startDate(LocalDate.now())
                .endDate(null)
                .build();

        when(employeeDetailsRepository.findByUserEmail("mgr@company.com"))
                .thenReturn(Optional.of(managerEmp));
        when(employeeDetailsRepository.findById(3L)).thenReturn(Optional.of(regularEmp));

        assertThatThrownBy(() -> pipService.createPip(dto, "mgr@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Start date and end date are required");
    }

    @Test
    void createPip_endDateBeforeStartDate_throwsBadRequest() {
        PipDTO dto = PipDTO.builder()
                .employeeId(3L)
                .title("PIP")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().minusDays(1))  // end before start
                .build();

        when(employeeDetailsRepository.findByUserEmail("mgr@company.com"))
                .thenReturn(Optional.of(managerEmp));
        when(employeeDetailsRepository.findById(3L)).thenReturn(Optional.of(regularEmp));

        assertThatThrownBy(() -> pipService.createPip(dto, "mgr@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("End date must be after start date");
    }

    @Test
    void createPip_employeeNotFound_throwsResourceNotFoundException() {
        PipDTO dto = PipDTO.builder()
                .employeeId(999L)
                .title("PIP")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(3))
                .build();

        when(employeeDetailsRepository.findByUserEmail("mgr@company.com"))
                .thenReturn(Optional.of(managerEmp));
        when(employeeDetailsRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pipService.createPip(dto, "mgr@company.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Employee not found");
    }

    // ── updatePip ─────────────────────────────────────────────────────────────

    @Test
    void updatePip_existingPip_updatesFields() {
        PipDTO dto = PipDTO.builder()
                .title("Updated PIP Title")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(4))
                .reason("Updated reason")
                .build();

        when(pipRepository.findById(10L)).thenReturn(Optional.of(samplePip));
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com"))
                .thenReturn(Optional.of(managerEmp));
        when(pipRepository.save(any())).thenReturn(samplePip);
        stubPipCountQueries(10L);
        when(auditRepository.save(any())).thenReturn(PipAuditLog.builder().build());

        PipDTO result = pipService.updatePip(10L, dto, "mgr@company.com");

        assertThat(result).isNotNull();
        verify(pipRepository).save(samplePip);
    }

    @Test
    void updatePip_pipNotFound_throwsResourceNotFoundException() {
        when(pipRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pipService.updatePip(999L, PipDTO.builder().build(), "mgr@company.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── addGoal ───────────────────────────────────────────────────────────────

    @Test
    void addGoal_validInput_savesGoal() {
        PipGoalDTO goalDTO = PipGoalDTO.builder()
                .title("Goal A")
                .description("Detailed description")
                .successCriteria("Complete by Q3")
                .targetDate(LocalDate.now().plusWeeks(8))
                .build();

        PipGoal savedGoal = PipGoal.builder()
                .id(1L).pip(samplePip).title("Goal A")
                .progressPercent(0).status(PipGoalStatus.NOT_STARTED).build();

        when(pipRepository.findById(10L)).thenReturn(Optional.of(samplePip));
        when(employeeDetailsRepository.findByUserEmail("admin@company.com"))
                .thenReturn(Optional.of(adminEmp));
        when(goalRepository.save(any())).thenReturn(savedGoal);
        when(auditRepository.save(any())).thenReturn(PipAuditLog.builder().build());

        PipGoalDTO result = pipService.addGoal(10L, goalDTO, "admin@company.com");

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Goal A");
        assertThat(result.getStatus()).isEqualTo("NOT_STARTED");
    }

    // ── updateGoal ────────────────────────────────────────────────────────────

    @Test
    void updateGoal_validInput_updatesGoal() {
        PipGoal existingGoal = PipGoal.builder()
                .id(1L).pip(samplePip).title("Old Goal")
                .progressPercent(30).status(PipGoalStatus.IN_PROGRESS).build();

        PipGoalDTO dto = PipGoalDTO.builder()
                .title("Updated Goal").description("New desc")
                .progressPercent(75).status("IN_PROGRESS")
                .build();

        when(pipRepository.findById(10L)).thenReturn(Optional.of(samplePip));
        when(goalRepository.findById(1L)).thenReturn(Optional.of(existingGoal));
        when(employeeDetailsRepository.findByUserEmail("admin@company.com"))
                .thenReturn(Optional.of(adminEmp));
        when(goalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditRepository.save(any())).thenReturn(PipAuditLog.builder().build());

        PipGoalDTO result = pipService.updateGoal(10L, 1L, dto, "admin@company.com");

        assertThat(result).isNotNull();
        assertThat(result.getProgressPercent()).isEqualTo(75);
    }

    @Test
    void updateGoal_progressCappedAt100() {
        PipGoal existingGoal = PipGoal.builder()
                .id(1L).pip(samplePip).title("Goal")
                .progressPercent(0).status(PipGoalStatus.NOT_STARTED).build();

        PipGoalDTO dto = PipGoalDTO.builder()
                .title("Goal").progressPercent(150).status("ACHIEVED").build();

        when(pipRepository.findById(10L)).thenReturn(Optional.of(samplePip));
        when(goalRepository.findById(1L)).thenReturn(Optional.of(existingGoal));
        when(employeeDetailsRepository.findByUserEmail("admin@company.com"))
                .thenReturn(Optional.of(adminEmp));
        when(goalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditRepository.save(any())).thenReturn(PipAuditLog.builder().build());

        PipGoalDTO result = pipService.updateGoal(10L, 1L, dto, "admin@company.com");

        assertThat(result.getProgressPercent()).isEqualTo(100); // capped at 100
    }

    @Test
    void updateGoal_goalNotFound_throwsResourceNotFoundException() {
        when(pipRepository.findById(10L)).thenReturn(Optional.of(samplePip));
        when(goalRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pipService.updateGoal(10L, 999L, PipGoalDTO.builder()
                .title("X").status("NOT_STARTED").build(), "admin@company.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Goal not found");
    }

    @Test
    void updateGoal_goalBelongsToDifferentPip_throwsBadRequest() {
        PerformanceImprovementPlan otherPip = PerformanceImprovementPlan.builder()
                .id(99L).employee(regularEmp).createdBy(adminEmp)
                .title("Other PIP").startDate(LocalDate.now()).endDate(LocalDate.now().plusMonths(1))
                .status(PipStatus.ACTIVE).build();

        PipGoal goalForOtherPip = PipGoal.builder()
                .id(1L).pip(otherPip).title("Goal for Other PIP")
                .progressPercent(0).status(PipGoalStatus.NOT_STARTED).build();

        when(pipRepository.findById(10L)).thenReturn(Optional.of(samplePip));
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goalForOtherPip));

        assertThatThrownBy(() -> pipService.updateGoal(10L, 1L, PipGoalDTO.builder()
                .title("X").status("NOT_STARTED").build(), "admin@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Goal does not belong to the specified PIP");
    }

    // ── addWeeklyReview ───────────────────────────────────────────────────────

    @Test
    void addWeeklyReview_validInput_savesReview() {
        PipWeeklyReviewDTO dto = PipWeeklyReviewDTO.builder()
                .overallProgress("Good progress this week")
                .progressRating(4)
                .achievements("Completed task A")
                .challenges("Time management")
                .reviewDate(LocalDate.now())
                .build();

        PipWeeklyReview saved = PipWeeklyReview.builder()
                .id(1L).pip(samplePip).weekNumber(1)
                .conductedBy(managerEmp)
                .overallProgress("Good progress this week")
                .progressRating(4)
                .reviewDate(LocalDate.now())
                .build();

        when(pipRepository.findById(10L)).thenReturn(Optional.of(samplePip));
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com"))
                .thenReturn(Optional.of(managerEmp));
        when(reviewRepository.countByPipId(10L)).thenReturn(0L);
        when(reviewRepository.save(any())).thenReturn(saved);
        when(auditRepository.save(any())).thenReturn(PipAuditLog.builder().build());

        PipWeeklyReviewDTO result = pipService.addWeeklyReview(10L, dto, "mgr@company.com");

        assertThat(result).isNotNull();
        assertThat(result.getWeekNumber()).isEqualTo(1);
        verify(reviewRepository).save(any(PipWeeklyReview.class));
    }

    @Test
    void addWeeklyReview_progressRatingClamped() {
        PipWeeklyReviewDTO dto = PipWeeklyReviewDTO.builder()
                .overallProgress("Progress")
                .progressRating(10)  // out of bounds
                .reviewDate(LocalDate.now())
                .build();

        PipWeeklyReview saved = PipWeeklyReview.builder()
                .id(1L).pip(samplePip).weekNumber(2).conductedBy(adminEmp)
                .progressRating(5) // clamped to max 5
                .overallProgress("Progress").reviewDate(LocalDate.now()).build();

        when(pipRepository.findById(10L)).thenReturn(Optional.of(samplePip));
        when(employeeDetailsRepository.findByUserEmail("admin@company.com"))
                .thenReturn(Optional.of(adminEmp));
        when(reviewRepository.countByPipId(10L)).thenReturn(1L);
        when(reviewRepository.save(any())).thenReturn(saved);
        when(auditRepository.save(any())).thenReturn(PipAuditLog.builder().build());

        PipWeeklyReviewDTO result = pipService.addWeeklyReview(10L, dto, "admin@company.com");

        assertThat(result.getProgressRating()).isEqualTo(5); // clamped
    }

    // ── addComment ────────────────────────────────────────────────────────────

    @Test
    void addComment_validContent_savesComment() {
        User authorUser = User.builder().id(1L).email("admin@company.com").role(Role.ADMIN).active(true).build();
        EmployeeDetails author = EmployeeDetails.builder().id(1L).user(authorUser)
                .firstName("Admin").lastName("User").active(true).build();
        authorUser.setEmployeeDetails(author);

        PipComment savedComment = PipComment.builder()
                .id(1L).pip(samplePip).author(author).content("Great improvement!").build();

        when(pipRepository.findById(10L)).thenReturn(Optional.of(samplePip));
        when(employeeDetailsRepository.findByUserEmail("admin@company.com"))
                .thenReturn(Optional.of(author));
        when(commentRepository.save(any())).thenReturn(savedComment);
        when(auditRepository.save(any())).thenReturn(PipAuditLog.builder().build());
        doNothing().when(pipNotificationService).notifyComment(any(), any(), any());

        PipCommentDTO result = pipService.addComment(10L, "Great improvement!", "admin@company.com");

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEqualTo("Great improvement!");
        verify(pipNotificationService).notifyComment(any(), any(), any());
    }

    @Test
    void addComment_blankContent_throwsBadRequest() {
        when(pipRepository.findById(10L)).thenReturn(Optional.of(samplePip));
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com"))
                .thenReturn(Optional.of(managerEmp));

        assertThatThrownBy(() -> pipService.addComment(10L, "  ", "mgr@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Comment content cannot be empty");
    }

    @Test
    void addComment_nullContent_throwsBadRequest() {
        when(pipRepository.findById(10L)).thenReturn(Optional.of(samplePip));
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com"))
                .thenReturn(Optional.of(managerEmp));

        assertThatThrownBy(() -> pipService.addComment(10L, null, "mgr@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Comment content cannot be empty");
    }

    // ── setOutcome ────────────────────────────────────────────────────────────

    @Test
    void setOutcome_completedStatus_updatesAndSaves() {
        PipOutcomeDTO dto = PipOutcomeDTO.builder()
                .status("COMPLETED")
                .finalNotes("Employee met all goals")
                .outcomeDate(LocalDate.now())
                .build();

        when(pipRepository.findById(10L)).thenReturn(Optional.of(samplePip));
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com"))
                .thenReturn(Optional.of(managerEmp));
        when(pipRepository.save(any())).thenReturn(samplePip);
        stubPipCountQueries(10L);
        when(auditRepository.save(any())).thenReturn(PipAuditLog.builder().build());
        when(userRepository.findByRoleIn(any())).thenReturn(List.of());

        PipDTO result = pipService.setOutcome(10L, dto, "mgr@company.com");

        assertThat(result).isNotNull();
        verify(pipRepository).save(samplePip);
    }

    @Test
    void setOutcome_invalidStatus_throwsBadRequest() {
        PipOutcomeDTO dto = PipOutcomeDTO.builder()
                .status("INVALID_STATUS")
                .build();

        when(pipRepository.findById(10L)).thenReturn(Optional.of(samplePip));
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com"))
                .thenReturn(Optional.of(managerEmp));

        assertThatThrownBy(() -> pipService.setOutcome(10L, dto, "mgr@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid outcome status");
    }

    @Test
    void setOutcome_activeStatus_throwsBadRequest() {
        PipOutcomeDTO dto = PipOutcomeDTO.builder()
                .status("ACTIVE")
                .build();

        when(pipRepository.findById(10L)).thenReturn(Optional.of(samplePip));
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com"))
                .thenReturn(Optional.of(managerEmp));

        assertThatThrownBy(() -> pipService.setOutcome(10L, dto, "mgr@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot set outcome status to ACTIVE");
    }

    @Test
    void setOutcome_extendedWithNewEndDate_updatesEndDateAndKeepsActive() {
        PipOutcomeDTO dto = PipOutcomeDTO.builder()
                .status("EXTENDED")
                .newEndDate(LocalDate.now().plusMonths(6))
                .outcomeDate(LocalDate.now())
                .build();

        when(pipRepository.findById(10L)).thenReturn(Optional.of(samplePip));
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com"))
                .thenReturn(Optional.of(managerEmp));
        when(pipRepository.save(any())).thenReturn(samplePip);
        stubPipCountQueries(10L);
        when(auditRepository.save(any())).thenReturn(PipAuditLog.builder().build());
        when(userRepository.findByRoleIn(any())).thenReturn(List.of());

        PipDTO result = pipService.setOutcome(10L, dto, "mgr@company.com");

        assertThat(result).isNotNull();
        // Extended should set status back to ACTIVE
        verify(pipRepository).save(argThat(p ->
                ((PerformanceImprovementPlan) p).getStatus() == PipStatus.ACTIVE));
    }

    // ── deletePip ─────────────────────────────────────────────────────────────

    @Test
    void deletePip_existingPip_deletesSuccessfully() {
        when(pipRepository.findById(10L)).thenReturn(Optional.of(samplePip));
        when(employeeDetailsRepository.findByUserEmail("admin@company.com"))
                .thenReturn(Optional.of(adminEmp));
        doNothing().when(pipRepository).delete(samplePip);

        pipService.deletePip(10L, "admin@company.com");

        verify(pipRepository).delete(samplePip);
    }

    @Test
    void deletePip_pipNotFound_throwsResourceNotFoundException() {
        when(pipRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pipService.deletePip(999L, "admin@company.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("PIP not found");
    }

    // ── deleteGoal ────────────────────────────────────────────────────────────

    @Test
    void deleteGoal_existingGoal_deletesSuccessfully() {
        PipGoal goal = PipGoal.builder()
                .id(1L).pip(samplePip).title("Goal to Delete")
                .progressPercent(0).status(PipGoalStatus.NOT_STARTED).build();

        when(pipRepository.findById(10L)).thenReturn(Optional.of(samplePip));
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(employeeDetailsRepository.findByUserEmail("admin@company.com"))
                .thenReturn(Optional.of(adminEmp));
        doNothing().when(goalRepository).delete(goal);
        when(auditRepository.save(any())).thenReturn(PipAuditLog.builder().build());

        pipService.deleteGoal(10L, 1L, "admin@company.com");

        verify(goalRepository).delete(goal);
    }

    @Test
    void deleteGoal_goalNotFound_throwsResourceNotFoundException() {
        when(pipRepository.findById(10L)).thenReturn(Optional.of(samplePip));
        when(goalRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pipService.deleteGoal(10L, 999L, "admin@company.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Goal not found");
    }

    // ── toListDTO stats ───────────────────────────────────────────────────────

    @Test
    void getPips_overdueFlag_setWhenActivePipPastEndDate() {
        PerformanceImprovementPlan overduePip = PerformanceImprovementPlan.builder()
                .id(11L)
                .employee(regularEmp)
                .createdBy(managerEmp)
                .title("Overdue PIP")
                .startDate(LocalDate.now().minusMonths(6))
                .endDate(LocalDate.now().minusDays(1))  // past end date
                .status(PipStatus.ACTIVE)  // still active
                .build();

        when(employeeDetailsRepository.findByUserEmail("admin@company.com"))
                .thenReturn(Optional.of(adminEmp));
        when(pipRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(overduePip));
        when(pipRepository.countTotalGoals(11L)).thenReturn(0L);
        when(pipRepository.countAchievedGoals(11L)).thenReturn(0L);
        when(pipRepository.countWeeklyReviews(11L)).thenReturn(0L);
        when(pipRepository.countComments(11L)).thenReturn(0L);
        when(pipRepository.avgGoalProgress(11L)).thenReturn(null);

        List<PipDTO> result = pipService.getPips("admin@company.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isOverdue()).isTrue();
    }

    @Test
    void getPips_nullAvgProgress_usesZero() {
        when(employeeDetailsRepository.findByUserEmail("admin@company.com"))
                .thenReturn(Optional.of(adminEmp));
        when(pipRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(samplePip));
        when(pipRepository.countTotalGoals(10L)).thenReturn(0L);
        when(pipRepository.countAchievedGoals(10L)).thenReturn(0L);
        when(pipRepository.countWeeklyReviews(10L)).thenReturn(0L);
        when(pipRepository.countComments(10L)).thenReturn(0L);
        when(pipRepository.avgGoalProgress(10L)).thenReturn(null);  // null avg

        List<PipDTO> result = pipService.getPips("admin@company.com");

        assertThat(result.get(0).getOverallProgressPercent()).isEqualTo(0);
    }
}
