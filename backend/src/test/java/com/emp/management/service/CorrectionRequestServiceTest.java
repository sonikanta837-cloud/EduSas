package com.emp.management.service;

import com.emp.management.dto.AuditLogDTO;
import com.emp.management.dto.CorrectionRequestDTO;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CorrectionRequestServiceTest {

    @Mock private CorrectionRequestRepository correctionRequestRepository;
    @Mock private TimesheetAuditLogRepository auditLogRepository;
    @Mock private AttendanceSessionRepository sessionRepository;
    @Mock private TimesheetRepository timesheetRepository;
    @Mock private EmployeeDetailsRepository employeeDetailsRepository;
    @Mock private UserRepository userRepository;
    @Mock private EmailService emailService;

    @InjectMocks private CorrectionRequestService correctionRequestService;

    private EmployeeDetails employee;
    private EmployeeDetails manager;
    private AttendanceSession pendingSession;
    private TimesheetCorrectionRequest pendingRequest;

    @BeforeEach
    void setUp() {
        // Set @Value fields that are not initialized by @InjectMocks (no Spring context)
        ReflectionTestUtils.setField(correctionRequestService, "correctionEnabled", true);
        ReflectionTestUtils.setField(correctionRequestService, "correctionWindowDays", 3);
        ReflectionTestUtils.setField(correctionRequestService, "emailNotificationsEnabled", false);
        ReflectionTestUtils.setField(correctionRequestService, "hrNotificationsEnabled", false);
        ReflectionTestUtils.setField(correctionRequestService, "uploadDir", "./uploads");

        User mgrUser = User.builder().id(1L).email("mgr@company.com").role(Role.MANAGER).active(true).build();
        manager = EmployeeDetails.builder().id(1L).user(mgrUser)
                .firstName("Jane").lastName("Manager").active(true).build();
        mgrUser.setEmployeeDetails(manager);

        User empUser = User.builder().id(2L).email("emp@company.com").role(Role.EMPLOYEE).active(true).build();
        employee = EmployeeDetails.builder().id(2L).user(empUser)
                .firstName("John").lastName("Doe").manager(manager)
                .employeeCode("EMP10002").department("Engineering").active(true).build();
        empUser.setEmployeeDetails(employee);

        pendingSession = AttendanceSession.builder()
                .id(5L).employee(employee)
                .workDate(LocalDate.now())
                .loginTime(LocalTime.of(9, 0))
                .status(AttendanceSessionStatus.PENDING_LOGOUT_CONFIRMATION)
                .build();

        pendingRequest = TimesheetCorrectionRequest.builder()
                .id(1L).employee(employee).session(pendingSession)
                .workDate(LocalDate.now())
                .loginTime(LocalTime.of(9, 0))
                .requestedLogoutTime(LocalTime.of(18, 0))
                .reason("Forgot to check out")
                .status(CorrectionStatus.PENDING_MANAGER_APPROVAL)
                .build();
    }

    // ── getPendingSessions ────────────────────────────────────────────────────

    @Test
    void getPendingSessions_returnsFilteredSessions() {
        when(employeeDetailsRepository.findByUserEmail("emp@company.com"))
                .thenReturn(Optional.of(employee));
        when(sessionRepository.findByEmployeeIdAndStatusOrderByWorkDateDesc(
                2L, AttendanceSessionStatus.PENDING_LOGOUT_CONFIRMATION))
                .thenReturn(List.of(pendingSession));

        var result = correctionRequestService.getPendingSessions("emp@company.com");

        assertThat(result).hasSize(1);
    }

    @Test
    void getPendingSessions_unknownEmployee_returnsEmptyList() {
        when(employeeDetailsRepository.findByUserEmail("ghost@company.com"))
                .thenReturn(Optional.empty());

        var result = correctionRequestService.getPendingSessions("ghost@company.com");

        assertThat(result).isEmpty();
    }

    @Test
    void getPendingSessions_sessionTooOld_filtered() {
        // Session 10 days ago — beyond correctionWindowDays (default 3)
        AttendanceSession oldSession = AttendanceSession.builder()
                .id(6L).employee(employee)
                .workDate(LocalDate.now().minusDays(10))
                .loginTime(LocalTime.of(9, 0))
                .status(AttendanceSessionStatus.PENDING_LOGOUT_CONFIRMATION)
                .build();

        when(employeeDetailsRepository.findByUserEmail("emp@company.com"))
                .thenReturn(Optional.of(employee));
        when(sessionRepository.findByEmployeeIdAndStatusOrderByWorkDateDesc(
                2L, AttendanceSessionStatus.PENDING_LOGOUT_CONFIRMATION))
                .thenReturn(List.of(oldSession));

        var result = correctionRequestService.getPendingSessions("emp@company.com");

        // Old session filtered out
        assertThat(result).isEmpty();
    }

    // ── submit ────────────────────────────────────────────────────────────────

    @Test
    void submit_validRequest_createsCorrection() {
        when(employeeDetailsRepository.findByUserEmail("emp@company.com"))
                .thenReturn(Optional.of(employee));
        when(sessionRepository.findById(5L)).thenReturn(Optional.of(pendingSession));
        when(correctionRequestRepository.existsBySessionIdAndStatus(5L, CorrectionStatus.PENDING_MANAGER_APPROVAL))
                .thenReturn(false);
        when(correctionRequestRepository.save(any())).thenReturn(pendingRequest);
        when(auditLogRepository.save(any())).thenReturn(TimesheetAuditLog.builder().build());

        CorrectionRequestDTO result = correctionRequestService.submit(
                "emp@company.com", 5L, "18:00:00", "Forgot to check out", null);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("PENDING_MANAGER_APPROVAL");
        verify(correctionRequestRepository).save(any(TimesheetCorrectionRequest.class));
    }

    @Test
    void submit_employeeNotFound_throwsBadRequest() {
        when(employeeDetailsRepository.findByUserEmail("unknown@company.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> correctionRequestService.submit(
                "unknown@company.com", 5L, "18:00:00", "reason", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Employee profile not found");
    }

    @Test
    void submit_sessionNotFound_throwsResourceNotFoundException() {
        when(employeeDetailsRepository.findByUserEmail("emp@company.com"))
                .thenReturn(Optional.of(employee));
        when(sessionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> correctionRequestService.submit(
                "emp@company.com", 999L, "18:00:00", "reason", null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void submit_sessionBelongsToDifferentEmployee_throwsBadRequest() {
        User otherUser = User.builder().id(3L).email("other@company.com").role(Role.EMPLOYEE).build();
        EmployeeDetails otherEmp = EmployeeDetails.builder().id(3L).user(otherUser).active(true).build();
        otherUser.setEmployeeDetails(otherEmp);

        // Session belongs to otherEmp but submitter is employee (id=2)
        AttendanceSession otherSession = AttendanceSession.builder()
                .id(5L).employee(otherEmp)
                .workDate(LocalDate.now())
                .loginTime(LocalTime.of(9, 0))
                .status(AttendanceSessionStatus.PENDING_LOGOUT_CONFIRMATION)
                .build();

        when(employeeDetailsRepository.findByUserEmail("emp@company.com"))
                .thenReturn(Optional.of(employee));
        when(sessionRepository.findById(5L)).thenReturn(Optional.of(otherSession));

        assertThatThrownBy(() -> correctionRequestService.submit(
                "emp@company.com", 5L, "18:00:00", "reason", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Session does not belong to this employee");
    }

    @Test
    void submit_sessionNotPending_throwsBadRequest() {
        AttendanceSession completedSession = AttendanceSession.builder()
                .id(5L).employee(employee)
                .workDate(LocalDate.now())
                .loginTime(LocalTime.of(9, 0))
                .logoutTime(LocalTime.of(18, 0))
                .status(AttendanceSessionStatus.COMPLETE)  // already complete
                .build();

        when(employeeDetailsRepository.findByUserEmail("emp@company.com"))
                .thenReturn(Optional.of(employee));
        when(sessionRepository.findById(5L)).thenReturn(Optional.of(completedSession));

        assertThatThrownBy(() -> correctionRequestService.submit(
                "emp@company.com", 5L, "18:00:00", "reason", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not pending logout confirmation");
    }

    @Test
    void submit_pendingRequestAlreadyExists_throwsBadRequest() {
        when(employeeDetailsRepository.findByUserEmail("emp@company.com"))
                .thenReturn(Optional.of(employee));
        when(sessionRepository.findById(5L)).thenReturn(Optional.of(pendingSession));
        when(correctionRequestRepository.existsBySessionIdAndStatus(5L, CorrectionStatus.PENDING_MANAGER_APPROVAL))
                .thenReturn(true);  // already exists

        assertThatThrownBy(() -> correctionRequestService.submit(
                "emp@company.com", 5L, "18:00:00", "reason", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("pending correction request already exists");
    }

    @Test
    void submit_logoutBeforeLogin_throwsBadRequest() {
        // login at 9:00, requested logout at 08:00 (before login)
        when(employeeDetailsRepository.findByUserEmail("emp@company.com"))
                .thenReturn(Optional.of(employee));
        when(sessionRepository.findById(5L)).thenReturn(Optional.of(pendingSession));
        when(correctionRequestRepository.existsBySessionIdAndStatus(5L, CorrectionStatus.PENDING_MANAGER_APPROVAL))
                .thenReturn(false);

        assertThatThrownBy(() -> correctionRequestService.submit(
                "emp@company.com", 5L, "08:00:00", "reason", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("logout time must be after login time");
    }

    // ── getMyRequests ─────────────────────────────────────────────────────────

    @Test
    void getMyRequests_returnsOwnRequests() {
        when(employeeDetailsRepository.findByUserEmail("emp@company.com"))
                .thenReturn(Optional.of(employee));
        when(correctionRequestRepository.findByEmployeeIdOrderByCreatedAtDesc(2L))
                .thenReturn(List.of(pendingRequest));

        List<CorrectionRequestDTO> result = correctionRequestService.getMyRequests("emp@company.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getReason()).isEqualTo("Forgot to check out");
    }

    @Test
    void getMyRequests_unknownEmployee_returnsEmptyList() {
        when(employeeDetailsRepository.findByUserEmail("ghost@company.com"))
                .thenReturn(Optional.empty());

        List<CorrectionRequestDTO> result = correctionRequestService.getMyRequests("ghost@company.com");

        assertThat(result).isEmpty();
    }

    // ── getAllPending ─────────────────────────────────────────────────────────

    @Test
    void getAllPending_returnsNonAdminPending() {
        when(correctionRequestRepository.findByStatusOrderByCreatedAtDesc(CorrectionStatus.PENDING_MANAGER_APPROVAL))
                .thenReturn(List.of(pendingRequest));

        List<CorrectionRequestDTO> result = correctionRequestService.getAllPending();

        // Employee role (not ADMIN/DIRECTOR) — should be included
        assertThat(result).hasSize(1);
    }

    @Test
    void getAllPending_adminRequests_filtered() {
        // Create a request from an ADMIN employee — should be filtered out
        User adminUser = User.builder().id(10L).email("admin@company.com").role(Role.ADMIN).build();
        EmployeeDetails adminEmp = EmployeeDetails.builder().id(10L).user(adminUser).active(true).build();
        adminUser.setEmployeeDetails(adminEmp);

        TimesheetCorrectionRequest adminRequest = TimesheetCorrectionRequest.builder()
                .id(2L).employee(adminEmp).session(pendingSession)
                .workDate(LocalDate.now()).loginTime(LocalTime.of(9, 0))
                .requestedLogoutTime(LocalTime.of(18, 0))
                .reason("Admin forgot to check out")
                .status(CorrectionStatus.PENDING_MANAGER_APPROVAL)
                .build();

        when(correctionRequestRepository.findByStatusOrderByCreatedAtDesc(CorrectionStatus.PENDING_MANAGER_APPROVAL))
                .thenReturn(List.of(adminRequest));

        List<CorrectionRequestDTO> result = correctionRequestService.getAllPending();

        // ADMIN request should be filtered out
        assertThat(result).isEmpty();
    }

    // ── getPendingForTeam ─────────────────────────────────────────────────────

    @Test
    void getPendingForTeam_returnsSubordinateRequests() {
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com"))
                .thenReturn(Optional.of(manager));
        when(correctionRequestRepository.findByEmployee_Manager_IdAndStatusOrderByCreatedAtDesc(
                1L, CorrectionStatus.PENDING_MANAGER_APPROVAL))
                .thenReturn(List.of(pendingRequest));

        List<CorrectionRequestDTO> result = correctionRequestService.getPendingForTeam("mgr@company.com");

        assertThat(result).hasSize(1);
    }

    @Test
    void getPendingForTeam_unknownManager_returnsEmptyList() {
        when(employeeDetailsRepository.findByUserEmail("ghost@company.com"))
                .thenReturn(Optional.empty());

        List<CorrectionRequestDTO> result = correctionRequestService.getPendingForTeam("ghost@company.com");

        assertThat(result).isEmpty();
    }

    // ── approve ───────────────────────────────────────────────────────────────

    @Test
    void approve_validRequest_approvesAndUpdatesSession() {
        User approverUser = User.builder().id(1L).email("mgr@company.com").role(Role.MANAGER).build();
        when(correctionRequestRepository.findById(1L)).thenReturn(Optional.of(pendingRequest));
        when(userRepository.findByEmail("mgr@company.com")).thenReturn(Optional.of(approverUser));
        when(employeeDetailsRepository.findByUserId(1L)).thenReturn(Optional.of(manager));
        when(sessionRepository.findByEmployeeIdAndWorkDateOrderByLoginTimeAsc(anyLong(), any()))
                .thenReturn(List.of(pendingSession));
        when(timesheetRepository.findByEmployeeIdAndWorkDate(anyLong(), any()))
                .thenReturn(Optional.empty());
        when(correctionRequestRepository.save(any())).thenReturn(pendingRequest);
        when(sessionRepository.save(any())).thenReturn(pendingSession);
        lenient().when(timesheetRepository.save(any())).thenReturn(Timesheet.builder().build());
        when(auditLogRepository.save(any())).thenReturn(TimesheetAuditLog.builder().build());
        lenient().when(userRepository.findByRoleIn(any())).thenReturn(List.of());

        CorrectionRequestDTO result = correctionRequestService.approve(
                1L, "mgr@company.com", "18:00:00", "Approved — looks correct");

        assertThat(result).isNotNull();
        verify(sessionRepository).save(argThat(s ->
                ((AttendanceSession) s).getStatus() == AttendanceSessionStatus.COMPLETE));
    }

    @Test
    void approve_requestNotFound_throwsResourceNotFoundException() {
        when(correctionRequestRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> correctionRequestService.approve(
                999L, "mgr@company.com", "18:00:00", ""))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void approve_requestAlreadyResolved_throwsBadRequest() {
        TimesheetCorrectionRequest resolved = TimesheetCorrectionRequest.builder()
                .id(1L).employee(employee).session(pendingSession)
                .workDate(LocalDate.now()).loginTime(LocalTime.of(9, 0))
                .requestedLogoutTime(LocalTime.of(18, 0))
                .status(CorrectionStatus.APPROVED)  // already resolved
                .reason("Already done").build();

        when(correctionRequestRepository.findById(1L)).thenReturn(Optional.of(resolved));

        assertThatThrownBy(() -> correctionRequestService.approve(
                1L, "mgr@company.com", "18:00:00", ""))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no longer in pending state");
    }

    // ── reject ────────────────────────────────────────────────────────────────

    @Test
    void reject_validRequest_rejectsRequest() {
        User rejectorUser = User.builder().id(1L).email("mgr@company.com").role(Role.MANAGER).build();
        when(correctionRequestRepository.findById(1L)).thenReturn(Optional.of(pendingRequest));
        when(userRepository.findByEmail("mgr@company.com")).thenReturn(Optional.of(rejectorUser));
        when(employeeDetailsRepository.findByUserId(1L)).thenReturn(Optional.of(manager));
        when(correctionRequestRepository.save(any())).thenReturn(pendingRequest);
        when(auditLogRepository.save(any())).thenReturn(TimesheetAuditLog.builder().build());
        lenient().when(userRepository.findByRoleIn(any())).thenReturn(List.of());

        CorrectionRequestDTO result = correctionRequestService.reject(
                1L, "mgr@company.com", "Insufficient justification");

        assertThat(result).isNotNull();
        verify(correctionRequestRepository).save(argThat(r ->
                ((TimesheetCorrectionRequest) r).getStatus() == CorrectionStatus.REJECTED));
    }

    @Test
    void reject_approverNotFound_throwsBadRequest() {
        when(correctionRequestRepository.findById(1L)).thenReturn(Optional.of(pendingRequest));
        when(userRepository.findByEmail("nobody@company.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> correctionRequestService.reject(
                1L, "nobody@company.com", "comment"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Rejector not found");
    }

    // ── getAuditLogs ──────────────────────────────────────────────────────────

    @Test
    void getAuditLogs_returnsAllLogs() {
        TimesheetAuditLog log = TimesheetAuditLog.builder()
                .id(1L).employee(employee)
                .workDate(LocalDate.now())
                .action("SUBMITTED")
                .approverName("Jane Manager")
                .build();

        when(auditLogRepository.findAllByOrderByActionDateTimeDesc()).thenReturn(List.of(log));

        List<AuditLogDTO> result = correctionRequestService.getAuditLogs();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAction()).isEqualTo("SUBMITTED");
    }

    @Test
    void getAuditLogs_emptyRepository_returnsEmptyList() {
        when(auditLogRepository.findAllByOrderByActionDateTimeDesc()).thenReturn(List.of());

        List<AuditLogDTO> result = correctionRequestService.getAuditLogs();

        assertThat(result).isEmpty();
    }
}
