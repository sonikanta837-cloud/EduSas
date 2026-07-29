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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PipServiceTest {

    @Mock private PipRepository pipRepository;
    @Mock private PipGoalRepository goalRepository;
    @Mock private PipWeeklyReviewRepository reviewRepository;
    @Mock private PipCommentRepository commentRepository;
    @Mock private PipAuditLogRepository auditRepository;
    @Mock private EmployeeDetailsRepository employeeDetailsRepository;
    @Mock private UserRepository userRepository;
    @Mock private EmailService emailService;
    @Mock private PipNotificationService pipNotificationService;

    @InjectMocks private PipService pipService;

    private User empUser;
    private EmployeeDetails employee;
    private User mgrUser;
    private EmployeeDetails manager;
    private PerformanceImprovementPlan pip;

    @BeforeEach
    void setUp() {
        mgrUser = User.builder().id(1L).email("mgr@company.com").role(Role.MANAGER).active(true).build();
        manager = EmployeeDetails.builder().id(1L).user(mgrUser).firstName("Jane").lastName("Manager").active(true).build();

        empUser = User.builder().id(2L).email("emp@company.com").role(Role.EMPLOYEE).active(true).build();
        employee = EmployeeDetails.builder().id(2L).user(empUser).firstName("John").lastName("Doe")
                .manager(manager).active(true).build();

        pip = PerformanceImprovementPlan.builder()
                .id(100L).employee(employee).createdBy(manager)
                .title("Improve delivery").startDate(LocalDate.now().minusDays(10)).endDate(LocalDate.now().plusDays(20))
                .status(PipStatus.ACTIVE)
                .build();

        lenient().when(pipRepository.countTotalGoals(anyLong())).thenReturn(0L);
        lenient().when(pipRepository.countAchievedGoals(anyLong())).thenReturn(0L);
        lenient().when(pipRepository.countWeeklyReviews(anyLong())).thenReturn(0L);
        lenient().when(pipRepository.countComments(anyLong())).thenReturn(0L);
        lenient().when(pipRepository.avgGoalProgress(anyLong())).thenReturn(null);
    }

    // ── getPips — role-scoped visibility ─────────────────────────────────────

    @Test
    void getPips_asAdmin_returnsAllOrgWide() {
        User adminUser = User.builder().id(9L).email("admin@company.com").role(Role.ADMIN).active(true).build();
        EmployeeDetails admin = EmployeeDetails.builder().id(9L).user(adminUser).active(true).build();
        when(employeeDetailsRepository.findByUserEmail("admin@company.com")).thenReturn(Optional.of(admin));
        when(pipRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(pip));

        List<PipDTO> result = pipService.getPips("admin@company.com");

        assertThat(result).hasSize(1);
        verify(pipRepository, never()).findByEmployeeIdOrderByCreatedAtDesc(anyLong());
    }

    @Test
    void getPips_asManager_dedupesAcrossOwnSubordinateAndCreatedSources() {
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com")).thenReturn(Optional.of(manager));
        // Same PIP surfaces via both "subordinate" and "created by" queries — must not duplicate
        when(pipRepository.findByEmployeeIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        when(pipRepository.findByEmployeeManagerId(1L)).thenReturn(List.of(pip));
        when(pipRepository.findByCreatedByIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(pip));

        List<PipDTO> result = pipService.getPips("mgr@company.com");

        assertThat(result).hasSize(1);
    }

    @Test
    void getPips_asEmployee_returnsOnlyOwnPips() {
        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));
        when(pipRepository.findByEmployeeIdOrderByCreatedAtDesc(2L)).thenReturn(List.of(pip));

        List<PipDTO> result = pipService.getPips("emp@company.com");

        assertThat(result).hasSize(1);
        verify(pipRepository, never()).findAllByOrderByCreatedAtDesc();
    }

    // ── getPipsByEmployee — self vs. others ──────────────────────────────────

    @Test
    void getPipsByEmployee_employeeRequestingOwnRecords_succeeds() {
        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));
        when(pipRepository.findByEmployeeIdOrderByCreatedAtDesc(2L)).thenReturn(List.of(pip));

        assertThat(pipService.getPipsByEmployee(2L, "emp@company.com")).hasSize(1);
    }

    @Test
    void getPipsByEmployee_employeeRequestingSomeoneElses_throwsBadRequestException() {
        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> pipService.getPipsByEmployee(999L, "emp@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void getPipsByEmployee_managerRequestingSubordinate_succeeds() {
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com")).thenReturn(Optional.of(manager));
        when(pipRepository.findByEmployeeIdOrderByCreatedAtDesc(2L)).thenReturn(List.of(pip));

        assertThat(pipService.getPipsByEmployee(2L, "mgr@company.com")).hasSize(1);
    }

    // ── getPipDetail — self vs. others ────────────────────────────────────────

    @Test
    void getPipDetail_employeeViewingOwnPip_succeeds() {
        when(pipRepository.findById(100L)).thenReturn(Optional.of(pip));
        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));

        PipDTO result = pipService.getPipDetail(100L, "emp@company.com");

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getGoals()).isEmpty();
    }

    @Test
    void getPipDetail_employeeViewingSomeoneElsesPip_throwsBadRequestException() {
        User otherUser = User.builder().id(3L).email("other@company.com").role(Role.EMPLOYEE).active(true).build();
        EmployeeDetails other = EmployeeDetails.builder().id(3L).user(otherUser).active(true).build();
        when(pipRepository.findById(100L)).thenReturn(Optional.of(pip));
        when(employeeDetailsRepository.findByUserEmail("other@company.com")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> pipService.getPipDetail(100L, "other@company.com"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getPipDetail_notFound_throwsResourceNotFoundException() {
        when(pipRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pipService.getPipDetail(999L, "mgr@company.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── createPip ──────────────────────────────────────────────────────────────

    @Test
    void createPip_validDates_savesAuditsAndEmails() {
        PipDTO dto = PipDTO.builder().employeeId(2L).title("Improve delivery")
                .startDate(LocalDate.now()).endDate(LocalDate.now().plusDays(30)).build();

        when(employeeDetailsRepository.findByUserEmail("mgr@company.com")).thenReturn(Optional.of(manager));
        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(employee));
        when(pipRepository.save(any())).thenReturn(pip);
        when(pipRepository.findById(100L)).thenReturn(Optional.of(pip));

        PipDTO result = pipService.createPip(dto, "mgr@company.com");

        assertThat(result.getId()).isEqualTo(100L);
        verify(auditRepository).save(argThat(a -> "PIP_CREATED".equals(a.getAction())));
        verify(emailService).sendPipCreatedEmail(eq("emp@company.com"), any(), anyString(), anyString(),
                anyString(), any(), any(), any(), any());
    }

    @Test
    void createPip_endDateBeforeStartDate_throwsBadRequestException() {
        PipDTO dto = PipDTO.builder().employeeId(2L)
                .startDate(LocalDate.now().plusDays(10)).endDate(LocalDate.now()).build();

        when(employeeDetailsRepository.findByUserEmail("mgr@company.com")).thenReturn(Optional.of(manager));
        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> pipService.createPip(dto, "mgr@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("End date must be after start date");
    }

    @Test
    void createPip_missingDates_throwsBadRequestException() {
        PipDTO dto = PipDTO.builder().employeeId(2L).build();

        when(employeeDetailsRepository.findByUserEmail("mgr@company.com")).thenReturn(Optional.of(manager));
        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> pipService.createPip(dto, "mgr@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Start date and end date are required");
    }

    @Test
    void createPip_unknownEmployee_throwsResourceNotFoundException() {
        PipDTO dto = PipDTO.builder().employeeId(999L)
                .startDate(LocalDate.now()).endDate(LocalDate.now().plusDays(1)).build();

        when(employeeDetailsRepository.findByUserEmail("mgr@company.com")).thenReturn(Optional.of(manager));
        when(employeeDetailsRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pipService.createPip(dto, "mgr@company.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── updatePip ──────────────────────────────────────────────────────────────

    @Test
    void updatePip_updatesFieldsAndWritesAuditLog() {
        PipDTO dto = PipDTO.builder().title("Revised title")
                .startDate(LocalDate.now()).endDate(LocalDate.now().plusDays(45)).build();

        when(pipRepository.findById(100L)).thenReturn(Optional.of(pip));
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com")).thenReturn(Optional.of(manager));
        when(pipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PipDTO result = pipService.updatePip(100L, dto, "mgr@company.com");

        assertThat(result.getTitle()).isEqualTo("Revised title");
        verify(auditRepository).save(argThat(a -> "PIP_UPDATED".equals(a.getAction())));
    }

    // ── goals ──────────────────────────────────────────────────────────────────

    @Test
    void addGoal_savesAndAudits() {
        PipGoalDTO dto = PipGoalDTO.builder().title("Reduce bugs").description("desc").build();
        when(pipRepository.findById(100L)).thenReturn(Optional.of(pip));
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com")).thenReturn(Optional.of(manager));
        when(goalRepository.save(any())).thenAnswer(inv -> {
            PipGoal g = inv.getArgument(0);
            g.setId(1L);
            return g;
        });

        PipGoalDTO result = pipService.addGoal(100L, dto, "mgr@company.com");

        assertThat(result.getStatus()).isEqualTo("NOT_STARTED");
        assertThat(result.getProgressPercent()).isEqualTo(0);
        verify(auditRepository).save(argThat(a -> "GOAL_ADDED".equals(a.getAction())));
    }

    @Test
    void updateGoal_progressPercentAboveMax_isClampedTo100() {
        PipGoal existingGoal = PipGoal.builder().id(1L).pip(pip).title("g").status(PipGoalStatus.IN_PROGRESS).build();
        PipGoalDTO dto = PipGoalDTO.builder().title("g").progressPercent(150).status("ACHIEVED").build();

        when(pipRepository.findById(100L)).thenReturn(Optional.of(pip));
        when(goalRepository.findById(1L)).thenReturn(Optional.of(existingGoal));
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com")).thenReturn(Optional.of(manager));
        when(goalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PipGoalDTO result = pipService.updateGoal(100L, 1L, dto, "mgr@company.com");

        assertThat(result.getProgressPercent()).isEqualTo(100);
    }

    @Test
    void updateGoal_progressPercentBelowMin_isClampedTo0() {
        PipGoal existingGoal = PipGoal.builder().id(1L).pip(pip).title("g").status(PipGoalStatus.NOT_STARTED).build();
        PipGoalDTO dto = PipGoalDTO.builder().title("g").progressPercent(-20).status("NOT_STARTED").build();

        when(pipRepository.findById(100L)).thenReturn(Optional.of(pip));
        when(goalRepository.findById(1L)).thenReturn(Optional.of(existingGoal));
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com")).thenReturn(Optional.of(manager));
        when(goalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PipGoalDTO result = pipService.updateGoal(100L, 1L, dto, "mgr@company.com");

        assertThat(result.getProgressPercent()).isEqualTo(0);
    }

    @Test
    void updateGoal_goalBelongsToDifferentPip_throwsBadRequestException() {
        PerformanceImprovementPlan otherPip = PerformanceImprovementPlan.builder().id(200L).build();
        PipGoal goalOfOtherPip = PipGoal.builder().id(1L).pip(otherPip).title("g").build();

        when(pipRepository.findById(100L)).thenReturn(Optional.of(pip));
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goalOfOtherPip));

        assertThatThrownBy(() -> pipService.updateGoal(100L, 1L, PipGoalDTO.builder().status("ACHIEVED").build(), "mgr@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    void deleteGoal_goalBelongsToDifferentPip_throwsBadRequestException() {
        PerformanceImprovementPlan otherPip = PerformanceImprovementPlan.builder().id(200L).build();
        PipGoal goalOfOtherPip = PipGoal.builder().id(1L).pip(otherPip).title("g").build();

        when(pipRepository.findById(100L)).thenReturn(Optional.of(pip));
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goalOfOtherPip));

        assertThatThrownBy(() -> pipService.deleteGoal(100L, 1L, "mgr@company.com"))
                .isInstanceOf(BadRequestException.class);

        verify(goalRepository, never()).delete(any());
    }

    @Test
    void deleteGoal_belongsToCorrectPip_deletesAndAudits() {
        PipGoal goal = PipGoal.builder().id(1L).pip(pip).title("g").build();
        when(pipRepository.findById(100L)).thenReturn(Optional.of(pip));
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com")).thenReturn(Optional.of(manager));

        pipService.deleteGoal(100L, 1L, "mgr@company.com");

        verify(goalRepository).delete(goal);
        verify(auditRepository).save(argThat(a -> "GOAL_DELETED".equals(a.getAction())));
    }

    // ── weekly reviews ─────────────────────────────────────────────────────────

    @Test
    void addWeeklyReview_incrementsWeekNumberFromExistingCount() {
        PipWeeklyReviewDTO dto = PipWeeklyReviewDTO.builder().overallProgress("Good").progressRating(4).build();
        when(pipRepository.findById(100L)).thenReturn(Optional.of(pip));
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com")).thenReturn(Optional.of(manager));
        when(reviewRepository.countByPipId(100L)).thenReturn(2L);
        when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PipWeeklyReviewDTO result = pipService.addWeeklyReview(100L, dto, "mgr@company.com");

        assertThat(result.getWeekNumber()).isEqualTo(3);
    }

    @Test
    void addWeeklyReview_ratingAboveMax_isClampedTo5() {
        PipWeeklyReviewDTO dto = PipWeeklyReviewDTO.builder().progressRating(9).build();
        when(pipRepository.findById(100L)).thenReturn(Optional.of(pip));
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com")).thenReturn(Optional.of(manager));
        when(reviewRepository.countByPipId(100L)).thenReturn(0L);
        when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PipWeeklyReviewDTO result = pipService.addWeeklyReview(100L, dto, "mgr@company.com");

        assertThat(result.getProgressRating()).isEqualTo(5);
    }

    @Test
    void addWeeklyReview_ratingBelowMin_isClampedTo1() {
        PipWeeklyReviewDTO dto = PipWeeklyReviewDTO.builder().progressRating(0).build();
        when(pipRepository.findById(100L)).thenReturn(Optional.of(pip));
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com")).thenReturn(Optional.of(manager));
        when(reviewRepository.countByPipId(100L)).thenReturn(0L);
        when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PipWeeklyReviewDTO result = pipService.addWeeklyReview(100L, dto, "mgr@company.com");

        assertThat(result.getProgressRating()).isEqualTo(1);
    }

    // ── comments ───────────────────────────────────────────────────────────────

    @Test
    void addComment_blankContent_throwsBadRequestException() {
        when(pipRepository.findById(100L)).thenReturn(Optional.of(pip));
        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> pipService.addComment(100L, "   ", "emp@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cannot be empty");
    }

    @Test
    void addComment_validContent_savesAuditsAndNotifies() {
        when(pipRepository.findById(100L)).thenReturn(Optional.of(pip));
        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));
        when(commentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PipCommentDTO result = pipService.addComment(100L, "  Keep it up  ", "emp@company.com");

        assertThat(result.getContent()).isEqualTo("Keep it up"); // trimmed
        verify(pipNotificationService).notifyComment(eq(pip), eq(employee), eq("Keep it up"));
        verify(auditRepository).save(argThat(a -> "COMMENT_ADDED".equals(a.getAction())));
    }

    // ── outcome ────────────────────────────────────────────────────────────────

    @Test
    void setOutcome_completed_setsStatusAndSendsEmail() {
        PipOutcomeDTO dto = PipOutcomeDTO.builder().status("COMPLETED").finalNotes("Great improvement").build();
        when(pipRepository.findById(100L)).thenReturn(Optional.of(pip));
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com")).thenReturn(Optional.of(manager));
        when(pipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PipDTO result = pipService.setOutcome(100L, dto, "mgr@company.com");

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        verify(emailService).sendPipOutcomeEmail(eq("emp@company.com"), any(), anyString(), anyString(),
                eq("COMPLETED"), any(), anyString());
    }

    @Test
    void setOutcome_terminated_setsStatus() {
        PipOutcomeDTO dto = PipOutcomeDTO.builder().status("TERMINATED").finalNotes("No improvement").build();
        when(pipRepository.findById(100L)).thenReturn(Optional.of(pip));
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com")).thenReturn(Optional.of(manager));
        when(pipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PipDTO result = pipService.setOutcome(100L, dto, "mgr@company.com");

        assertThat(result.getStatus()).isEqualTo("TERMINATED");
    }

    @Test
    void setOutcome_extended_remainsActiveWithNewEndDate() {
        LocalDate newEnd = LocalDate.now().plusDays(60);
        PipOutcomeDTO dto = PipOutcomeDTO.builder().status("EXTENDED").newEndDate(newEnd).build();
        when(pipRepository.findById(100L)).thenReturn(Optional.of(pip));
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com")).thenReturn(Optional.of(manager));
        when(pipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PipDTO result = pipService.setOutcome(100L, dto, "mgr@company.com");

        assertThat(result.getStatus()).isEqualTo("ACTIVE"); // extended = still active
        assertThat(pip.getEndDate()).isEqualTo(newEnd);
    }

    @Test
    void setOutcome_invalidStatusString_throwsBadRequestException() {
        PipOutcomeDTO dto = PipOutcomeDTO.builder().status("CANCELLED").build();
        when(pipRepository.findById(100L)).thenReturn(Optional.of(pip));
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com")).thenReturn(Optional.of(manager));

        assertThatThrownBy(() -> pipService.setOutcome(100L, dto, "mgr@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid outcome status");
    }

    @Test
    void setOutcome_explicitlyActive_throwsBadRequestException() {
        PipOutcomeDTO dto = PipOutcomeDTO.builder().status("ACTIVE").build();
        when(pipRepository.findById(100L)).thenReturn(Optional.of(pip));
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com")).thenReturn(Optional.of(manager));

        assertThatThrownBy(() -> pipService.setOutcome(100L, dto, "mgr@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot set outcome status to ACTIVE");
    }

    // ── delete ─────────────────────────────────────────────────────────────────

    @Test
    void deletePip_removesRow() {
        when(pipRepository.findById(100L)).thenReturn(Optional.of(pip));
        when(employeeDetailsRepository.findByUserEmail("admin@company.com")).thenReturn(Optional.of(manager));

        pipService.deletePip(100L, "admin@company.com");

        verify(pipRepository).delete(pip);
    }

    @Test
    void deletePip_notFound_throwsResourceNotFoundException() {
        when(pipRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pipService.deletePip(999L, "admin@company.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
