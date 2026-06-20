package com.emp.management.service;

import com.emp.management.entity.*;
import com.emp.management.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for TimesheetService scheduled/audit methods that are not covered in
 * the base TimesheetServiceTest (which covers the basic CRUD/query methods).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TimesheetServiceScheduledTest {

    @Mock private TimesheetRepository timesheetRepository;
    @Mock private AttendanceSessionRepository sessionRepository;
    @Mock private EmployeeDetailsRepository employeeDetailsRepository;
    @Mock private UserRepository userRepository;
    @Mock private LeaveRepository leaveRepository;
    @Mock private EmailService emailService;
    @Mock private TimesheetEntryRepository timesheetEntryRepository;
    @Mock private SystemSettingService systemSettingService;

    @InjectMocks private TimesheetService timesheetService;

    private EmployeeDetails employee;
    private User empUser;
    private EmployeeDetails manager;
    private User managerUser;

    @BeforeEach
    void setUp() {
        // Enable correction by default
        ReflectionTestUtils.setField(timesheetService, "correctionEnabled", true);
        ReflectionTestUtils.setField(timesheetService, "breakAlertEnabled", true);
        ReflectionTestUtils.setField(timesheetService, "breakAlertThresholdMinutes", 75);

        managerUser = User.builder().id(10L).email("mgr@company.com").role(Role.MANAGER).build();
        manager = EmployeeDetails.builder().id(10L).user(managerUser)
                .firstName("Jane").lastName("Mgr").active(true).build();
        managerUser.setEmployeeDetails(manager);

        empUser = User.builder().id(1L).email("emp@company.com").role(Role.EMPLOYEE).build();
        employee = EmployeeDetails.builder().id(1L).user(empUser)
                .firstName("John").lastName("Doe")
                .employeeCode("EMP10001").department("Engineering")
                .manager(manager).active(true).build();
        empUser.setEmployeeDetails(employee);
    }

    // ── runDailyAttendanceAudit ───────────────────────────────────────────────

    @Test
    void runDailyAttendanceAudit_employeeWorkedLessThan8hrs_sendsAlertToManager() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        // Skip if yesterday is a weekend (test only meaningful on weekdays)
        if (yesterday.getDayOfWeek().getValue() >= 6) return;

        Timesheet ts = Timesheet.builder()
                .id(1L).employee(employee).workDate(yesterday)
                .workingHours(5.0).alertSent(false).manualOverride(false).build();

        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(employee));
        when(leaveRepository.hasLeaveOnDate(eq(1L), eq(yesterday), eq(LeaveStatus.APPROVED))).thenReturn(false);
        when(timesheetRepository.findByEmployeeIdAndWorkDate(eq(1L), eq(yesterday))).thenReturn(Optional.of(ts));
        when(timesheetRepository.save(any())).thenReturn(ts);
        // HR and admin users (for CC)
        when(userRepository.findByRole(Role.HR)).thenReturn(List.of());
        when(userRepository.findByRoleIn(any())).thenReturn(List.of());

        timesheetService.runDailyAttendanceAudit();

        verify(emailService).sendAttendanceAuditAlert(
                eq("mgr@company.com"), any(), eq("John Doe"),
                eq("EMP10001"), eq("Engineering"), eq("emp@company.com"),
                eq(yesterday), eq(5.0), eq(8.0), anyDouble());
        verify(timesheetRepository).save(argThat(t -> t.isAlertSent()));
    }

    @Test
    void runDailyAttendanceAudit_employeeWorked8hrs_noAlert() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        if (yesterday.getDayOfWeek().getValue() >= 6) return;

        Timesheet ts = Timesheet.builder()
                .id(1L).employee(employee).workDate(yesterday)
                .workingHours(8.0).alertSent(false).build();

        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(employee));
        when(leaveRepository.hasLeaveOnDate(any(), any(), any())).thenReturn(false);
        when(timesheetRepository.findByEmployeeIdAndWorkDate(any(), any())).thenReturn(Optional.of(ts));

        timesheetService.runDailyAttendanceAudit();

        verify(emailService, never()).sendAttendanceAuditAlert(any(), any(), any(), any(), any(), any(), any(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void runDailyAttendanceAudit_alertAlreadySent_noAlert() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        if (yesterday.getDayOfWeek().getValue() >= 6) return;

        Timesheet ts = Timesheet.builder()
                .id(1L).employee(employee).workDate(yesterday)
                .workingHours(5.0).alertSent(true).build();

        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(employee));
        when(leaveRepository.hasLeaveOnDate(any(), any(), any())).thenReturn(false);
        when(timesheetRepository.findByEmployeeIdAndWorkDate(any(), any())).thenReturn(Optional.of(ts));

        timesheetService.runDailyAttendanceAudit();

        verify(emailService, never()).sendAttendanceAuditAlert(any(), any(), any(), any(), any(), any(), any(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void runDailyAttendanceAudit_employeeOnLeave_noAlert() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        if (yesterday.getDayOfWeek().getValue() >= 6) return;

        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(employee));
        when(leaveRepository.hasLeaveOnDate(eq(1L), eq(yesterday), eq(LeaveStatus.APPROVED))).thenReturn(true);

        timesheetService.runDailyAttendanceAudit();

        verify(emailService, never()).sendAttendanceAuditAlert(any(), any(), any(), any(), any(), any(), any(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void runDailyAttendanceAudit_manualOverride_noAlert() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        if (yesterday.getDayOfWeek().getValue() >= 6) return;

        Timesheet ts = Timesheet.builder()
                .id(1L).employee(employee).workDate(yesterday)
                .workingHours(3.0).alertSent(false).manualOverride(true).build();

        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(employee));
        when(leaveRepository.hasLeaveOnDate(any(), any(), any())).thenReturn(false);
        when(timesheetRepository.findByEmployeeIdAndWorkDate(any(), any())).thenReturn(Optional.of(ts));

        timesheetService.runDailyAttendanceAudit();

        verify(emailService, never()).sendAttendanceAuditAlert(any(), any(), any(), any(), any(), any(), any(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void runDailyAttendanceAudit_noTimesheet_createsAlertRow() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        if (yesterday.getDayOfWeek().getValue() >= 6) return;

        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(employee));
        when(leaveRepository.hasLeaveOnDate(any(), any(), any())).thenReturn(false);
        when(timesheetRepository.findByEmployeeIdAndWorkDate(any(), any())).thenReturn(Optional.empty());
        when(timesheetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findByRole(any())).thenReturn(List.of());
        when(userRepository.findByRoleIn(any())).thenReturn(List.of());

        timesheetService.runDailyAttendanceAudit();

        verify(timesheetRepository).save(argThat(t -> t.isAlertSent() && t.getEmployee().equals(employee)));
    }

    @Test
    void runDailyAttendanceAudit_noManager_fallsBackToAdmin() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        if (yesterday.getDayOfWeek().getValue() >= 6) return;

        // Employee without manager
        EmployeeDetails empNoMgr = EmployeeDetails.builder().id(2L).user(empUser)
                .firstName("Jane").lastName("Solo")
                .employeeCode("EMP10002").department("HR")
                .manager(null).active(true).build();

        Timesheet ts = Timesheet.builder()
                .id(2L).employee(empNoMgr).workDate(yesterday)
                .workingHours(4.0).alertSent(false).build();

        User adminUser = User.builder().id(99L).email("admin@company.com").role(Role.ADMIN).build();

        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(empNoMgr));
        when(leaveRepository.hasLeaveOnDate(any(), any(), any())).thenReturn(false);
        when(timesheetRepository.findByEmployeeIdAndWorkDate(any(), any())).thenReturn(Optional.of(ts));
        when(timesheetRepository.save(any())).thenReturn(ts);
        when(userRepository.findByRoleIn(any())).thenReturn(List.of(adminUser));
        when(userRepository.findByRole(any())).thenReturn(List.of());

        timesheetService.runDailyAttendanceAudit();

        verify(emailService).sendAttendanceAuditAlert(
                eq("admin@company.com"), any(), any(), any(), any(), any(),
                any(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void runDailyAttendanceAudit_noManagerAndNoAdmin_noEmailSent() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        if (yesterday.getDayOfWeek().getValue() >= 6) return;

        EmployeeDetails empNoMgr = EmployeeDetails.builder().id(2L).user(empUser)
                .firstName("Jane").lastName("Solo").department("HR")
                .manager(null).active(true).build();

        Timesheet ts = Timesheet.builder()
                .id(2L).employee(empNoMgr).workDate(yesterday)
                .workingHours(4.0).alertSent(false).build();

        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(empNoMgr));
        when(leaveRepository.hasLeaveOnDate(any(), any(), any())).thenReturn(false);
        when(timesheetRepository.findByEmployeeIdAndWorkDate(any(), any())).thenReturn(Optional.of(ts));
        when(userRepository.findByRoleIn(any())).thenReturn(List.of()); // no admins

        timesheetService.runDailyAttendanceAudit();

        verify(emailService, never()).sendAttendanceAuditAlert(any(), any(), any(), any(), any(), any(), any(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void runDailyAttendanceAudit_employeeWithNullUser_skipped() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        if (yesterday.getDayOfWeek().getValue() >= 6) return;

        EmployeeDetails empNoUser = EmployeeDetails.builder().id(3L).user(null)
                .firstName("Ghost").lastName("User").active(true).build();

        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(empNoUser));

        timesheetService.runDailyAttendanceAudit();

        verify(emailService, never()).sendAttendanceAuditAlert(any(), any(), any(), any(), any(), any(), any(), anyDouble(), anyDouble(), anyDouble());
        verify(leaveRepository, never()).hasLeaveOnDate(any(), any(), any());
    }

    // ── runMissingTimesheetAudit ──────────────────────────────────────────────

    @Test
    void runMissingTimesheetAudit_noTimesheetAndNoEntries_sendsAlert() {
        LocalDate auditDate = getLastWeekday();

        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(employee));
        when(leaveRepository.hasLeaveOnDate(eq(1L), eq(auditDate), eq(LeaveStatus.APPROVED))).thenReturn(false);
        when(timesheetRepository.findByEmployeeIdAndWorkDate(eq(1L), eq(auditDate))).thenReturn(Optional.empty());
        when(timesheetEntryRepository.existsByEmployeeIdAndDate(eq(1L), eq(auditDate))).thenReturn(false);
        when(timesheetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findByRole(any())).thenReturn(List.of());
        when(userRepository.findByRoleIn(any())).thenReturn(List.of());

        timesheetService.runMissingTimesheetAudit();

        verify(emailService).sendMissingTimesheetManagerAlert(
                eq("mgr@company.com"), any(), eq("John Doe"),
                eq("EMP10001"), eq("Engineering"), eq("emp@company.com"), eq(auditDate));
    }

    @Test
    void runMissingTimesheetAudit_timesheetEntryExists_noAlert() {
        LocalDate auditDate = getLastWeekday();

        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(employee));
        when(leaveRepository.hasLeaveOnDate(any(), any(), any())).thenReturn(false);
        when(timesheetRepository.findByEmployeeIdAndWorkDate(any(), any())).thenReturn(Optional.empty());
        when(timesheetEntryRepository.existsByEmployeeIdAndDate(eq(1L), eq(auditDate))).thenReturn(true);

        timesheetService.runMissingTimesheetAudit();

        verify(emailService, never()).sendMissingTimesheetManagerAlert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void runMissingTimesheetAudit_missingAlertAlreadySent_noAlert() {
        LocalDate auditDate = getLastWeekday();

        Timesheet ts = Timesheet.builder()
                .id(1L).employee(employee).workDate(auditDate)
                .missingAlertSent(true).build();

        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(employee));
        when(leaveRepository.hasLeaveOnDate(any(), any(), any())).thenReturn(false);
        when(timesheetRepository.findByEmployeeIdAndWorkDate(any(), any())).thenReturn(Optional.of(ts));

        timesheetService.runMissingTimesheetAudit();

        verify(emailService, never()).sendMissingTimesheetManagerAlert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void runMissingTimesheetAudit_employeeOnLeave_noAlert() {
        LocalDate auditDate = getLastWeekday();

        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(employee));
        when(leaveRepository.hasLeaveOnDate(eq(1L), eq(auditDate), eq(LeaveStatus.APPROVED))).thenReturn(true);

        timesheetService.runMissingTimesheetAudit();

        verify(emailService, never()).sendMissingTimesheetManagerAlert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void runMissingTimesheetAudit_manualOverride_noAlert() {
        LocalDate auditDate = getLastWeekday();

        Timesheet ts = Timesheet.builder()
                .id(1L).employee(employee).workDate(auditDate)
                .manualOverride(true).build();

        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(employee));
        when(leaveRepository.hasLeaveOnDate(any(), any(), any())).thenReturn(false);
        when(timesheetRepository.findByEmployeeIdAndWorkDate(any(), any())).thenReturn(Optional.of(ts));

        timesheetService.runMissingTimesheetAudit();

        verify(emailService, never()).sendMissingTimesheetManagerAlert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void runMissingTimesheetAudit_nullUser_skipped() {
        EmployeeDetails empNoUser = EmployeeDetails.builder().id(3L).user(null)
                .firstName("Ghost").active(true).build();

        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(empNoUser));

        timesheetService.runMissingTimesheetAudit();

        verify(emailService, never()).sendMissingTimesheetManagerAlert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void runMissingTimesheetAudit_existingTimesheetNoMissingAlert_setsFlag() {
        LocalDate auditDate = getLastWeekday();

        Timesheet ts = Timesheet.builder()
                .id(1L).employee(employee).workDate(auditDate)
                .missingAlertSent(false).build();

        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(employee));
        when(leaveRepository.hasLeaveOnDate(any(), any(), any())).thenReturn(false);
        when(timesheetRepository.findByEmployeeIdAndWorkDate(any(), any())).thenReturn(Optional.of(ts));
        when(timesheetEntryRepository.existsByEmployeeIdAndDate(any(), any())).thenReturn(false);
        when(timesheetRepository.save(any())).thenReturn(ts);
        when(userRepository.findByRole(any())).thenReturn(List.of());
        when(userRepository.findByRoleIn(any())).thenReturn(List.of());

        timesheetService.runMissingTimesheetAudit();

        verify(timesheetRepository).save(argThat(t -> t.isMissingAlertSent()));
    }

    // ── runMissingLogoutDetection ─────────────────────────────────────────────

    @Test
    void runMissingLogoutDetection_weekdayOpenSessions_markedPending() {
        // Use a weekday session (Monday)
        LocalDate monday = LocalDate.now().with(java.time.DayOfWeek.MONDAY);
        AttendanceSession openSession = AttendanceSession.builder()
                .id(1L).employee(employee)
                .workDate(monday)
                .loginTime(LocalTime.of(9, 0))
                .status(AttendanceSessionStatus.COMPLETE)
                .build();

        when(sessionRepository.findByWorkDateBeforeAndLogoutTimeIsNullAndStatus(
                any(), eq(AttendanceSessionStatus.COMPLETE)))
                .thenReturn(List.of(openSession));
        when(sessionRepository.save(any())).thenReturn(openSession);

        timesheetService.runMissingLogoutDetection();

        verify(sessionRepository).save(argThat(s ->
                ((AttendanceSession) s).getStatus() == AttendanceSessionStatus.PENDING_LOGOUT_CONFIRMATION));
    }

    @Test
    void runMissingLogoutDetection_weekendSessions_skipped() {
        LocalDate saturday = LocalDate.now().with(java.time.DayOfWeek.SATURDAY);
        AttendanceSession satSession = AttendanceSession.builder()
                .id(2L).employee(employee)
                .workDate(saturday)
                .loginTime(LocalTime.of(9, 0))
                .status(AttendanceSessionStatus.COMPLETE)
                .build();

        when(sessionRepository.findByWorkDateBeforeAndLogoutTimeIsNullAndStatus(
                any(), eq(AttendanceSessionStatus.COMPLETE)))
                .thenReturn(List.of(satSession));

        timesheetService.runMissingLogoutDetection();

        verify(sessionRepository, never()).save(any());
    }

    @Test
    void runMissingLogoutDetection_correctionDisabled_doesNothing() {
        ReflectionTestUtils.setField(timesheetService, "correctionEnabled", false);

        timesheetService.runMissingLogoutDetection();

        verify(sessionRepository, never()).findByWorkDateBeforeAndLogoutTimeIsNullAndStatus(any(), any());
    }

    @Test
    void runMissingLogoutDetection_emptySessions_noSave() {
        when(sessionRepository.findByWorkDateBeforeAndLogoutTimeIsNullAndStatus(any(), any()))
                .thenReturn(List.of());

        timesheetService.runMissingLogoutDetection();

        verify(sessionRepository, never()).save(any());
    }

    // ── runBreakTimeAudit ─────────────────────────────────────────────────────

    @Test
    void runBreakTimeAudit_disabled_doesNotProcess() {
        when(systemSettingService.getBoolean("break_alert_enabled", true)).thenReturn(false);

        timesheetService.runBreakTimeAudit();

        verify(employeeDetailsRepository, never()).findByActive(anyBoolean());
    }

    @Test
    void runBreakTimeAudit_enabled_processesEmployees() {
        // Only run on weekdays
        LocalDate today = LocalDate.now();
        if (today.getDayOfWeek().getValue() >= 6) return;

        when(systemSettingService.getBoolean("break_alert_enabled", true)).thenReturn(true);
        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(employee));
        when(timesheetRepository.findByEmployeeIdAndWorkDate(eq(1L), eq(today))).thenReturn(Optional.empty());
        when(leaveRepository.hasLeaveOnDate(eq(1L), eq(today), any())).thenReturn(true); // on leave — skip

        timesheetService.runBreakTimeAudit();

        // Processed but skipped due to leave
        verify(employeeDetailsRepository).findByActive(true);
    }

    @Test
    void runBreakTimeAudit_breakAlertAlreadySent_skipsEmployee() {
        LocalDate today = LocalDate.now();
        if (today.getDayOfWeek().getValue() >= 6) return;

        Timesheet ts = Timesheet.builder()
                .id(1L).employee(employee).workDate(today).breakAlertSent(true).build();

        when(systemSettingService.getBoolean("break_alert_enabled", true)).thenReturn(true);
        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(employee));
        when(timesheetRepository.findByEmployeeIdAndWorkDate(any(), any())).thenReturn(Optional.of(ts));
        lenient().when(leaveRepository.hasLeaveOnDate(any(), any(), any())).thenReturn(false);

        timesheetService.runBreakTimeAudit();

        verify(emailService, never()).sendBreakTimeAlertEmail(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyLong(), anyInt());
    }

    @Test
    void runBreakTimeAudit_nullUserEmployee_skipped() {
        LocalDate today = LocalDate.now();
        if (today.getDayOfWeek().getValue() >= 6) return;

        EmployeeDetails empNoUser = EmployeeDetails.builder().id(3L).user(null)
                .firstName("Ghost").active(true).build();

        when(systemSettingService.getBoolean("break_alert_enabled", true)).thenReturn(true);
        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(empNoUser));

        timesheetService.runBreakTimeAudit();

        verify(timesheetRepository, never()).findByEmployeeIdAndWorkDate(any(), any());
    }

    // ── checkAndSendBreakAlert — via checkOut ─────────────────────────────────

    @Test
    void checkOut_twoSessions_breakExceedsThreshold_sendsAlert() {
        LocalDate today = LocalDate.now();

        // Session 1: 9:00-12:00, Session 2: 14:00-17:00 → break = 120 mins > 75 threshold
        AttendanceSession session1 = AttendanceSession.builder()
                .id(1L).employee(employee).workDate(today)
                .loginTime(LocalTime.of(9, 0)).logoutTime(LocalTime.of(12, 0))
                .sessionHours(3.0).build();
        AttendanceSession openSession = AttendanceSession.builder()
                .id(2L).employee(employee).workDate(today)
                .loginTime(LocalTime.of(14, 0)).build();

        when(employeeDetailsRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));
        when(sessionRepository.findTopByEmployeeIdAndWorkDateAndLogoutTimeIsNullOrderByLoginTimeDesc(
                eq(1L), any())).thenReturn(Optional.of(openSession));
        when(sessionRepository.save(any())).thenAnswer(inv -> {
            AttendanceSession s = inv.getArgument(0);
            if (s.getLogoutTime() != null) {
                // Recalculate hours for the closed session
                s.setSessionHours(3.0);
            }
            return s;
        });

        // Return both sessions (1st already closed, 2nd now closed)
        AttendanceSession closedSession2 = AttendanceSession.builder()
                .id(2L).employee(employee).workDate(today)
                .loginTime(LocalTime.of(14, 0)).logoutTime(LocalTime.of(17, 0))
                .sessionHours(3.0).build();
        when(sessionRepository.findByEmployeeIdAndWorkDateOrderByLoginTimeAsc(eq(1L), any()))
                .thenReturn(List.of(session1, closedSession2));

        Timesheet ts = Timesheet.builder()
                .id(1L).employee(employee).workDate(today)
                .loginTime(LocalTime.of(9, 0)).alertSent(false).breakAlertSent(false).build();
        when(timesheetRepository.findByEmployeeIdAndWorkDate(eq(1L), any()))
                .thenReturn(Optional.of(ts));
        when(timesheetRepository.save(any())).thenReturn(ts);

        // Break alert system settings
        when(systemSettingService.getBoolean("break_alert_enabled", true)).thenReturn(true);
        when(systemSettingService.getString("break_alert_frequency", "BOTH")).thenReturn("BOTH");
        when(systemSettingService.getInt("break_alert_threshold_minutes", 75)).thenReturn(75);
        when(systemSettingService.getBoolean("break_alert_notify_manager", true)).thenReturn(true);
        when(systemSettingService.getBoolean("break_alert_notify_hr", true)).thenReturn(false);
        when(systemSettingService.getBoolean("break_alert_notify_admin", false)).thenReturn(false);
        when(userRepository.findByRole(any())).thenReturn(List.of());

        timesheetService.checkOut(1L);

        verify(emailService).sendBreakTimeAlertEmail(
                eq("mgr@company.com"), any(), eq("John Doe"), any(), any(), any(),
                any(), any(), any(), anyLong(), anyLong());
    }

    @Test
    void checkOut_singleSession_noBreakAlertSent() {
        LocalDate today = LocalDate.now();

        AttendanceSession openSession = AttendanceSession.builder()
                .id(1L).employee(employee).workDate(today)
                .loginTime(LocalTime.of(9, 0)).build();

        when(employeeDetailsRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));
        when(sessionRepository.findTopByEmployeeIdAndWorkDateAndLogoutTimeIsNullOrderByLoginTimeDesc(
                eq(1L), any())).thenReturn(Optional.of(openSession));
        when(sessionRepository.save(any())).thenReturn(openSession);

        AttendanceSession closedSession = AttendanceSession.builder()
                .id(1L).employee(employee).workDate(today)
                .loginTime(LocalTime.of(9, 0)).logoutTime(LocalTime.of(17, 0))
                .sessionHours(8.0).build();
        when(sessionRepository.findByEmployeeIdAndWorkDateOrderByLoginTimeAsc(eq(1L), any()))
                .thenReturn(List.of(closedSession));

        Timesheet ts = Timesheet.builder()
                .id(1L).employee(employee).workDate(today).build();
        when(timesheetRepository.findByEmployeeIdAndWorkDate(eq(1L), any()))
                .thenReturn(Optional.of(ts));
        when(timesheetRepository.save(any())).thenReturn(ts);

        when(systemSettingService.getBoolean("break_alert_enabled", true)).thenReturn(true);
        when(systemSettingService.getString("break_alert_frequency", "BOTH")).thenReturn("BOTH");

        timesheetService.checkOut(1L);

        // Only 1 session — can't compute break — no alert sent
        verify(emailService, never()).sendBreakTimeAlertEmail(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyLong(), anyInt());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Returns the last weekday (Mon-Fri). Handles the case where "yesterday"
     * is a weekend by walking back to Friday.
     */
    private LocalDate getLastWeekday() {
        LocalDate d = LocalDate.now().minusDays(1);
        while (d.getDayOfWeek().getValue() >= 6) {
            d = d.minusDays(1);
        }
        return d;
    }
}
