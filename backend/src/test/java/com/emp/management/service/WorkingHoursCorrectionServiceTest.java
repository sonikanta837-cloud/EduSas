package com.emp.management.service;

import com.emp.management.dto.JobDailySummaryDTO;
import com.emp.management.dto.WorkingHoursCorrectionRequestDTO;
import com.emp.management.dto.WorkingHoursCorrectionSubmitRequest;
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
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The soft UX gate over JobDailySummary: submit -> manager decides (HR view-only,
 * excluded from decide()). Regenerating on APPROVED must go through the same
 * generateForEmployee pipeline as the nightly rollup — no parallel calculation.
 */
@ExtendWith(MockitoExtension.class)
class WorkingHoursCorrectionServiceTest {

    @Mock private WorkingHoursCorrectionRequestRepository correctionRequestRepository;
    @Mock private WorkingHoursCorrectionAuditLogRepository auditLogRepository;
    @Mock private CorrectionNotificationService correctionNotificationService;
    @Mock private JobDailySummaryRepository summaryRepository;
    @Mock private JobDailySummaryService jobDailySummaryService;
    @Mock private JobWorkSessionRepository sessionRepository;
    @Mock private JobSessionBreakRepository breakRepository;
    @Mock private EmployeeDetailsRepository employeeDetailsRepository;
    @Mock private UserRepository userRepository;
    @Mock private EmailService emailService;
    @Mock private SystemSettingService systemSettingService;

    @InjectMocks private WorkingHoursCorrectionService correctionService;

    private User empUser;
    private EmployeeDetails employee;
    private User mgrUser;
    private EmployeeDetails manager;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        mgrUser = User.builder().id(1L).email("mgr@company.com").role(Role.MANAGER).active(true).build();
        manager = EmployeeDetails.builder().id(1L).user(mgrUser).firstName("Jane").lastName("Manager").active(true).build();

        empUser = User.builder().id(2L).email("emp@company.com").role(Role.EMPLOYEE).active(true).build();
        employee = EmployeeDetails.builder().id(2L).user(empUser).firstName("John").lastName("Doe")
                .employeeCode("10002").manager(manager).active(true).build();

        today = LocalDate.now();

        lenient().when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(employee));
        lenient().when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));
        lenient().when(systemSettingService.getInt(eq(SystemSettingService.KEY_CORR_OVERTIME_THRESHOLD_MINUTES), anyInt()))
                .thenReturn(480);
    }

    private WorkingHoursCorrectionSubmitRequest breakRequest(LocalDate date, LocalDateTime start, LocalDateTime end) {
        return WorkingHoursCorrectionSubmitRequest.builder()
                .workDate(date).reason(CorrectionReason.FORGOT_START_BREAK)
                .requestedBreakStartTime(start).requestedBreakEndTime(end)
                .build();
    }

    private JobDailySummaryDTO existingSummary() {
        return JobDailySummaryDTO.builder()
                .totalWorkingMinutes(480).totalBreakMinutes(0).totalOfficeMinutes(480)
                .overtimeMinutes(0).status("PRESENT").build();
    }

    // ── submit — date / duplicate guards ─────────────────────────────────────

    @Test
    void submit_futureDate_throwsBadRequestException() {
        WorkingHoursCorrectionSubmitRequest req = breakRequest(
                today.plusDays(1), today.plusDays(1).atTime(10, 0), today.plusDays(1).atTime(10, 20));

        assertThatThrownBy(() -> correctionService.submit(2L, req, "emp@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("future date");
    }

    @Test
    void submit_existingPendingRequestForDate_throwsBadRequestException() {
        WorkingHoursCorrectionSubmitRequest req = breakRequest(today, today.atTime(10, 0), today.atTime(10, 20));
        when(correctionRequestRepository.existsByEmployeeIdAndWorkDateAndStatus(2L, today, CorrectionRequestStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> correctionService.submit(2L, req, "emp@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already pending");
    }

    // ── submit — reason-specific validation ──────────────────────────────────

    @Test
    void submit_breakReason_missingBreakTimes_throwsBadRequestException() {
        WorkingHoursCorrectionSubmitRequest req = WorkingHoursCorrectionSubmitRequest.builder()
                .workDate(today).reason(CorrectionReason.FORGOT_START_BREAK).build();
        when(correctionRequestRepository.existsByEmployeeIdAndWorkDateAndStatus(any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> correctionService.submit(2L, req, "emp@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Break start time and end time are required");
    }

    @Test
    void submit_breakReason_endBeforeStart_throwsBadRequestException() {
        WorkingHoursCorrectionSubmitRequest req = breakRequest(today, today.atTime(14, 0), today.atTime(13, 0));
        when(correctionRequestRepository.existsByEmployeeIdAndWorkDateAndStatus(any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> correctionService.submit(2L, req, "emp@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("must be after");
    }

    @Test
    void submit_breakReason_noRecordedSessionForDate_throwsBadRequestException() {
        WorkingHoursCorrectionSubmitRequest req = breakRequest(today, today.atTime(10, 0), today.atTime(10, 20));
        when(correctionRequestRepository.existsByEmployeeIdAndWorkDateAndStatus(any(), any(), any())).thenReturn(false);
        when(sessionRepository.findByEmployeeIdAndWorkDateOrderByLoginTimeAsc(2L, today)).thenReturn(List.of());

        assertThatThrownBy(() -> correctionService.submit(2L, req, "emp@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No recorded session");
    }

    @Test
    void submit_breakReason_multiSession_breakOutsideAnySession_throwsBadRequestException() {
        LocalDateTime breakStart = today.atTime(13, 30);
        LocalDateTime breakEnd = today.atTime(13, 50);
        WorkingHoursCorrectionSubmitRequest req = breakRequest(today, breakStart, breakEnd);

        JobWorkSession session1 = JobWorkSession.builder().id(10L).employee(employee).workDate(today)
                .loginTime(today.atTime(9, 0)).logoutTime(today.atTime(12, 0)).build();
        JobWorkSession session2 = JobWorkSession.builder().id(11L).employee(employee).workDate(today)
                .loginTime(today.atTime(14, 0)).logoutTime(today.atTime(18, 0)).build();

        when(correctionRequestRepository.existsByEmployeeIdAndWorkDateAndStatus(any(), any(), any())).thenReturn(false);
        when(sessionRepository.findByEmployeeIdAndWorkDateOrderByLoginTimeAsc(2L, today))
                .thenReturn(List.of(session1, session2));

        assertThatThrownBy(() -> correctionService.submit(2L, req, "emp@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("does not fall within any recorded session");
    }

    @Test
    void submit_approvedOvertime_missingRemarks_throwsBadRequestException() {
        WorkingHoursCorrectionSubmitRequest req = WorkingHoursCorrectionSubmitRequest.builder()
                .workDate(today).reason(CorrectionReason.APPROVED_OVERTIME).build();
        when(correctionRequestRepository.existsByEmployeeIdAndWorkDateAndStatus(any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> correctionService.submit(2L, req, "emp@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Overtime remarks are required");
    }

    @Test
    void submit_otherReason_missingComments_throwsBadRequestException() {
        WorkingHoursCorrectionSubmitRequest req = WorkingHoursCorrectionSubmitRequest.builder()
                .workDate(today).reason(CorrectionReason.OTHER).build();
        when(correctionRequestRepository.existsByEmployeeIdAndWorkDateAndStatus(any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> correctionService.submit(2L, req, "emp@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Comments are required");
    }

    @Test
    void submit_noTimesheetDataForDate_throwsBadRequestException() {
        WorkingHoursCorrectionSubmitRequest req = WorkingHoursCorrectionSubmitRequest.builder()
                .workDate(today).reason(CorrectionReason.CLIENT_REQUIREMENT).reasonComments("n/a").build();
        // CLIENT_REQUIREMENT isn't a break reason and isn't APPROVED_OVERTIME/OTHER, so no
        // reason-specific field is required — it should still fail on missing timesheet data.
        when(correctionRequestRepository.existsByEmployeeIdAndWorkDateAndStatus(any(), any(), any())).thenReturn(false);
        when(jobDailySummaryService.getForDate(2L, today)).thenReturn(null);

        assertThatThrownBy(() -> correctionService.submit(2L, req, "emp@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No timesheet data found");
    }

    // ── submit — happy path ───────────────────────────────────────────────────

    @Test
    void submit_breakReason_singleSession_autoResolvesAndSaves() {
        LocalDateTime breakStart = today.atTime(10, 0);
        LocalDateTime breakEnd = today.atTime(10, 20);
        WorkingHoursCorrectionSubmitRequest req = breakRequest(today, breakStart, breakEnd);

        JobWorkSession onlySession = JobWorkSession.builder().id(10L).employee(employee).workDate(today)
                .loginTime(today.atTime(9, 0)).logoutTime(today.atTime(18, 0)).build();

        when(correctionRequestRepository.existsByEmployeeIdAndWorkDateAndStatus(any(), any(), any())).thenReturn(false);
        when(sessionRepository.findByEmployeeIdAndWorkDateOrderByLoginTimeAsc(2L, today))
                .thenReturn(List.of(onlySession));
        when(jobDailySummaryService.getForDate(2L, today)).thenReturn(existingSummary());
        when(jobDailySummaryService.computeSummary(eq(employee), eq(today), anyList(), any(), anyMap()))
                .thenReturn(new JobDailySummaryService.Computed(460, 20, 1, null, null, null, DailyAttendanceStatus.UNDER_HOURS));
        when(summaryRepository.findByEmployeeIdAndWorkDate(2L, today))
                .thenReturn(Optional.of(JobDailySummary.builder().id(99L).employee(employee).workDate(today).build()));
        when(correctionRequestRepository.save(any())).thenAnswer(inv -> {
            WorkingHoursCorrectionRequest r = inv.getArgument(0);
            r.setId(500L);
            r.setSubmittedAt(LocalDateTime.now());
            return r;
        });

        WorkingHoursCorrectionRequestDTO result = correctionService.submit(2L, req, "emp@company.com");

        assertThat(result).isNotNull();
        assertThat(result.getRequestedWorkingMinutes()).isEqualTo(460);
        assertThat(result.getRequestedStatus()).isEqualTo("UNDER_HOURS");
        verify(auditLogRepository).save(argThat(log -> "SUBMITTED".equals(log.getAction())));
    }

    @Test
    void submit_nonSelfNonPrivilegedCaller_throwsAccessDeniedException() {
        User otherUser = User.builder().id(3L).email("other@company.com").role(Role.EMPLOYEE).active(true).build();
        EmployeeDetails other = EmployeeDetails.builder().id(3L).user(otherUser).active(true).build();
        when(employeeDetailsRepository.findByUserEmail("other@company.com")).thenReturn(Optional.of(other));

        WorkingHoursCorrectionSubmitRequest req = breakRequest(today, today.atTime(10, 0), today.atTime(10, 20));

        assertThatThrownBy(() -> correctionService.submit(2L, req, "other@company.com"))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ── decide ────────────────────────────────────────────────────────────────

    private WorkingHoursCorrectionRequest pendingBreakRequest() {
        JobWorkSession session = JobWorkSession.builder().id(10L).employee(employee).workDate(today).build();
        return WorkingHoursCorrectionRequest.builder()
                .id(500L).employee(employee).workDate(today)
                .jobWorkSession(session)
                .reason(CorrectionReason.FORGOT_START_BREAK)
                .requestedBreakStartTime(today.atTime(10, 0)).requestedBreakEndTime(today.atTime(10, 20))
                .status(CorrectionRequestStatus.PENDING)
                .build();
    }

    @Test
    void decide_approve_breakReason_mutatesBreakAndRegeneratesViaRealPipeline() {
        WorkingHoursCorrectionRequest request = pendingBreakRequest();
        when(correctionRequestRepository.findById(500L)).thenReturn(Optional.of(request));
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com")).thenReturn(Optional.of(manager));
        when(summaryRepository.findByEmployeeIdAndWorkDate(2L, today))
                .thenReturn(Optional.of(JobDailySummary.builder().id(99L).employee(employee).workDate(today).build()));
        when(breakRepository.findByJobWorkSessionIdAndBreakEndTimeIsNull(10L)).thenReturn(Optional.empty());
        JobDailySummary regenerated = JobDailySummary.builder().id(99L).employee(employee).workDate(today)
                .totalWorkingMinutes(460).totalBreakMinutes(20).status(DailyAttendanceStatus.UNDER_HOURS).build();
        when(jobDailySummaryService.generateForEmployee(employee, today)).thenReturn(regenerated);
        when(correctionRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WorkingHoursCorrectionRequestDTO result = correctionService.decide(500L, "mgr@company.com", CorrectionRequestStatus.APPROVED, "OK");

        assertThat(result.getStatus()).isEqualTo(CorrectionRequestStatus.APPROVED);
        assertThat(result.getFinalWorkingMinutes()).isEqualTo(460);
        verify(breakRepository).save(any());
        verify(jobDailySummaryService).generateForEmployee(employee, today); // real pipeline, not a duplicate calc
    }

    @Test
    void decide_reject_leavesOriginalSummaryDataUntouched() {
        WorkingHoursCorrectionRequest request = pendingBreakRequest();
        when(correctionRequestRepository.findById(500L)).thenReturn(Optional.of(request));
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com")).thenReturn(Optional.of(manager));
        JobDailySummary summary = JobDailySummary.builder().id(99L).employee(employee).workDate(today).build();
        when(summaryRepository.findByEmployeeIdAndWorkDate(2L, today)).thenReturn(Optional.of(summary));
        when(correctionRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WorkingHoursCorrectionRequestDTO result = correctionService.decide(500L, "mgr@company.com", CorrectionRequestStatus.REJECTED, "No");

        assertThat(result.getStatus()).isEqualTo(CorrectionRequestStatus.REJECTED);
        assertThat(summary.getCorrectionStatus()).isEqualTo(DayCorrectionStatus.REJECTED);
        verify(breakRepository, never()).save(any());
        verify(jobDailySummaryService, never()).generateForEmployee(any(), any());
    }

    @Test
    void decide_alreadyProcessedRequest_throwsBadRequestException() {
        WorkingHoursCorrectionRequest request = pendingBreakRequest();
        request.setStatus(CorrectionRequestStatus.APPROVED);
        when(correctionRequestRepository.findById(500L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> correctionService.decide(500L, "mgr@company.com", CorrectionRequestStatus.REJECTED, ""))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already processed");
    }

    @Test
    void decide_invalidDecisionValue_throwsBadRequestException() {
        assertThatThrownBy(() -> correctionService.decide(500L, "mgr@company.com", CorrectionRequestStatus.PENDING, ""))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid decision");

        verify(correctionRequestRepository, never()).findById(any());
    }

    @Test
    void decide_requestNotFound_throwsResourceNotFoundException() {
        when(correctionRequestRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> correctionService.decide(999L, "mgr@company.com", CorrectionRequestStatus.APPROVED, ""))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getMyRequests / getAuditTrail — access control ───────────────────────

    @Test
    void getMyRequests_nonSelfNonPrivilegedCaller_throwsAccessDeniedException() {
        User otherUser = User.builder().id(3L).email("other@company.com").role(Role.EMPLOYEE).active(true).build();
        EmployeeDetails other = EmployeeDetails.builder().id(3L).user(otherUser).active(true).build();
        when(employeeDetailsRepository.findByUserEmail("other@company.com")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> correctionService.getMyRequests(2L, "other@company.com"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getMyRequests_self_returnsOwnRequests() {
        when(correctionRequestRepository.findByEmployeeIdOrderBySubmittedAtDesc(2L))
                .thenReturn(List.of(pendingBreakRequest()));

        List<WorkingHoursCorrectionRequestDTO> result = correctionService.getMyRequests(2L, "emp@company.com");

        assertThat(result).hasSize(1);
    }

    @Test
    void getAuditTrail_hrRole_isAllowedToView_thoughNotToDecide() {
        WorkingHoursCorrectionRequest request = pendingBreakRequest();
        User hrUser = User.builder().id(4L).email("hr@company.com").role(Role.HR).active(true).build();
        EmployeeDetails hr = EmployeeDetails.builder().id(4L).user(hrUser).active(true).build();

        when(correctionRequestRepository.findById(500L)).thenReturn(Optional.of(request));
        when(employeeDetailsRepository.findByUserEmail("hr@company.com")).thenReturn(Optional.of(hr));
        when(auditLogRepository.findByCorrectionRequestIdOrderByCreatedAtAsc(500L)).thenReturn(List.of());

        assertThat(correctionService.getAuditTrail(500L, "hr@company.com")).isEmpty();
    }

    @Test
    void getAuditTrail_unrelatedEmployee_throwsAccessDeniedException() {
        WorkingHoursCorrectionRequest request = pendingBreakRequest();
        User otherUser = User.builder().id(5L).email("stranger@company.com").role(Role.EMPLOYEE).active(true).build();
        EmployeeDetails stranger = EmployeeDetails.builder().id(5L).user(otherUser).active(true).build();

        when(correctionRequestRepository.findById(500L)).thenReturn(Optional.of(request));
        when(employeeDetailsRepository.findByUserEmail("stranger@company.com")).thenReturn(Optional.of(stranger));

        assertThatThrownBy(() -> correctionService.getAuditTrail(500L, "stranger@company.com"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getAuditTrail_directManager_isAllowed() {
        WorkingHoursCorrectionRequest request = pendingBreakRequest();
        when(correctionRequestRepository.findById(500L)).thenReturn(Optional.of(request));
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com")).thenReturn(Optional.of(manager));
        when(auditLogRepository.findByCorrectionRequestIdOrderByCreatedAtAsc(500L)).thenReturn(List.of());

        assertThat(correctionService.getAuditTrail(500L, "mgr@company.com")).isEmpty();
    }

    // ── list queries ──────────────────────────────────────────────────────────

    @Test
    void getPendingForManager_delegatesToRepository() {
        when(correctionRequestRepository.findPendingForManager(1L)).thenReturn(List.of(pendingBreakRequest()));

        assertThat(correctionService.getPendingForManager(1L)).hasSize(1);
    }

    @Test
    void getAllForManager_delegatesToRepository() {
        when(correctionRequestRepository.findAllForManager(1L)).thenReturn(List.of(pendingBreakRequest()));

        assertThat(correctionService.getAllForManager(1L)).hasSize(1);
    }

    @Test
    void getAll_delegatesToRepository() {
        when(correctionRequestRepository.findAllOrderByPendingFirst()).thenReturn(List.of(pendingBreakRequest()));

        assertThat(correctionService.getAll()).hasSize(1);
    }
}
