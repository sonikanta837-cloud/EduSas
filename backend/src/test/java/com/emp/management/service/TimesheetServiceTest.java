package com.emp.management.service;

import com.emp.management.dto.AttendanceSessionDTO;
import com.emp.management.dto.TimesheetDTO;
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
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TimesheetServiceTest {

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
    private Timesheet timesheet;
    private AttendanceSession openSession;

    @BeforeEach
    void setUp() {
        empUser = User.builder().id(1L).email("emp@company.com").role(Role.EMPLOYEE).active(true).build();
        employee = EmployeeDetails.builder().id(1L).user(empUser).firstName("John").lastName("Doe")
                .active(true).build();
        empUser.setEmployeeDetails(employee);

        timesheet = Timesheet.builder()
                .id(10L)
                .employee(employee)
                .workDate(LocalDate.now())
                .loginTime(LocalTime.of(9, 0))
                .logoutTime(LocalTime.of(17, 0))
                .workingHours(8.0)
                .alertSent(false)
                .build();

        openSession = AttendanceSession.builder()
                .id(1L)
                .employee(employee)
                .workDate(LocalDate.now())
                .loginTime(LocalTime.of(9, 0))
                .build();
    }

    // ── getMyTimesheets ───────────────────────────────────────────────────────

    @Test
    void getMyTimesheets_returnsTimesheetList() {
        when(timesheetRepository.findByEmployeeIdOrderByWorkDateDesc(1L)).thenReturn(List.of(timesheet));

        List<TimesheetDTO> result = timesheetService.getMyTimesheets(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmployeeId()).isEqualTo(1L);
        assertThat(result.get(0).getWorkingHours()).isEqualTo(8.0);
    }

    @Test
    void getMyTimesheets_empty_returnsEmptyList() {
        when(timesheetRepository.findByEmployeeIdOrderByWorkDateDesc(99L)).thenReturn(List.of());

        List<TimesheetDTO> result = timesheetService.getMyTimesheets(99L);

        assertThat(result).isEmpty();
    }

    // ── getTodayTimesheet ─────────────────────────────────────────────────────

    @Test
    void getTodayTimesheet_existing_returnsDTO() {
        when(timesheetRepository.findByEmployeeIdAndWorkDate(eq(1L), any())).thenReturn(Optional.of(timesheet));

        TimesheetDTO result = timesheetService.getTodayTimesheet(1L);

        assertThat(result).isNotNull();
        assertThat(result.getWorkingHours()).isEqualTo(8.0);
    }

    @Test
    void getTodayTimesheet_notPresent_returnsNull() {
        when(timesheetRepository.findByEmployeeIdAndWorkDate(eq(1L), any())).thenReturn(Optional.empty());

        TimesheetDTO result = timesheetService.getTodayTimesheet(1L);

        assertThat(result).isNull();
    }

    // ── getTimesheetsByDateRange ──────────────────────────────────────────────

    @Test
    void getTimesheetsByDateRange_returnsFilteredList() {
        LocalDate start = LocalDate.now().minusDays(7);
        LocalDate end = LocalDate.now();
        when(timesheetRepository.findByEmployeeIdAndDateRange(1L, start, end)).thenReturn(List.of(timesheet));

        List<TimesheetDTO> result = timesheetService.getTimesheetsByDateRange(1L, start, end);

        assertThat(result).hasSize(1);
    }

    // ── getTimesheetsByDate ───────────────────────────────────────────────────

    @Test
    void getTimesheetsByDate_returnsAllForDate() {
        when(timesheetRepository.findByWorkDate(any())).thenReturn(List.of(timesheet));

        List<TimesheetDTO> result = timesheetService.getTimesheetsByDate(LocalDate.now());

        assertThat(result).hasSize(1);
    }

    // ── getTodaySessions ─────────────────────────────────────────────────────

    @Test
    void getTodaySessions_returnsSessionList() {
        when(sessionRepository.findByEmployeeIdAndWorkDateOrderByLoginTimeAsc(eq(1L), any()))
                .thenReturn(List.of(openSession));

        List<AttendanceSessionDTO> result = timesheetService.getTodaySessions(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmployeeId()).isEqualTo(1L);
    }

    // ── getSessionsByRange ────────────────────────────────────────────────────

    @Test
    void getSessionsByRange_returnsFilteredSessions() {
        LocalDate start = LocalDate.now().minusDays(7);
        LocalDate end = LocalDate.now();
        when(sessionRepository.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAscLoginTimeAsc(1L, start, end))
                .thenReturn(List.of(openSession));

        List<AttendanceSessionDTO> result = timesheetService.getSessionsByRange(1L, start, end);

        assertThat(result).hasSize(1);
    }

    // ── checkIn ──────────────────────────────────────────────────────────────

    @Test
    void checkIn_noOpenSession_createsSession() {
        when(employeeDetailsRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(employeeDetailsRepository.findByUserId(1L)).thenReturn(Optional.of(employee));
        when(sessionRepository.findTopByEmployeeIdAndWorkDateAndLogoutTimeIsNullOrderByLoginTimeDesc(
                eq(1L), any())).thenReturn(Optional.empty());
        when(sessionRepository.save(any())).thenReturn(openSession);
        when(timesheetRepository.findByEmployeeIdAndWorkDate(eq(1L), any())).thenReturn(Optional.of(timesheet));

        timesheetService.checkIn(1L);

        verify(sessionRepository).save(any(AttendanceSession.class));
    }

    @Test
    void checkIn_employeeNotFound_throwsResourceNotFoundException() {
        when(employeeDetailsRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> timesheetService.checkIn(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void checkIn_employeeNoUserAccount_throwsBadRequestException() {
        EmployeeDetails noUser = EmployeeDetails.builder().id(5L).user(null).build();
        when(employeeDetailsRepository.findById(5L)).thenReturn(Optional.of(noUser));

        assertThatThrownBy(() -> timesheetService.checkIn(5L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no associated user account");
    }

    // ── checkOut ─────────────────────────────────────────────────────────────

    @Test
    void checkOut_withOpenSession_closesSession() {
        openSession.setLoginTime(LocalTime.of(9, 0));

        when(employeeDetailsRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));
        when(sessionRepository.findTopByEmployeeIdAndWorkDateAndLogoutTimeIsNullOrderByLoginTimeDesc(
                eq(1L), any())).thenReturn(Optional.of(openSession));
        when(sessionRepository.save(any())).thenReturn(openSession);
        when(sessionRepository.findByEmployeeIdAndWorkDateOrderByLoginTimeAsc(eq(1L), any()))
                .thenReturn(List.of(openSession));
        when(timesheetRepository.findByEmployeeIdAndWorkDate(eq(1L), any())).thenReturn(Optional.of(timesheet));
        when(timesheetRepository.save(any())).thenReturn(timesheet);
        lenient().when(systemSettingService.getBreakAlertSettings()).thenReturn(new com.emp.management.dto.BreakAlertSettingsDTO());

        timesheetService.checkOut(1L);

        verify(sessionRepository).save(any(AttendanceSession.class));
    }

    @Test
    void checkOut_noOpenSession_doesNothing() {
        when(employeeDetailsRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));
        when(sessionRepository.findTopByEmployeeIdAndWorkDateAndLogoutTimeIsNullOrderByLoginTimeDesc(
                eq(1L), any())).thenReturn(Optional.empty());

        timesheetService.checkOut(1L);

        // No session found — nothing should be saved
        verify(sessionRepository, never()).save(any());
    }

    // ── recordLogin ───────────────────────────────────────────────────────────

    @Test
    void recordLogin_noOpenSession_createsNewSession() {
        when(employeeDetailsRepository.findByUserId(1L)).thenReturn(Optional.of(employee));
        when(sessionRepository.findTopByEmployeeIdAndWorkDateAndLogoutTimeIsNullOrderByLoginTimeDesc(
                eq(1L), any())).thenReturn(Optional.empty());
        when(sessionRepository.save(any())).thenReturn(openSession);
        when(timesheetRepository.findByEmployeeIdAndWorkDate(eq(1L), any())).thenReturn(Optional.empty());
        when(timesheetRepository.save(any())).thenReturn(timesheet);

        timesheetService.recordLogin(1L);

        verify(sessionRepository).save(any(AttendanceSession.class));
        verify(timesheetRepository).save(any(Timesheet.class));
    }

    @Test
    void recordLogin_employeeNotFound_doesNothing() {
        when(employeeDetailsRepository.findByUserId(999L)).thenReturn(Optional.empty());

        timesheetService.recordLogin(999L);

        verify(sessionRepository, never()).save(any());
    }

    @Test
    void recordLogin_openSessionExists_doesNotCreateDuplicate() {
        when(employeeDetailsRepository.findByUserId(1L)).thenReturn(Optional.of(employee));
        when(sessionRepository.findTopByEmployeeIdAndWorkDateAndLogoutTimeIsNullOrderByLoginTimeDesc(
                eq(1L), any())).thenReturn(Optional.of(openSession));

        timesheetService.recordLogin(1L);

        verify(sessionRepository, never()).save(any());
    }

    // ── recordLogout ──────────────────────────────────────────────────────────

    @Test
    void recordLogout_noEmployee_doesNothing() {
        when(employeeDetailsRepository.findByUserEmail("unknown@company.com")).thenReturn(Optional.empty());

        timesheetService.recordLogout("unknown@company.com");

        verify(sessionRepository, never()).save(any());
    }

    @Test
    void recordLogout_noOpenSession_doesNothing() {
        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));
        when(sessionRepository.findTopByEmployeeIdAndWorkDateAndLogoutTimeIsNullOrderByLoginTimeDesc(
                eq(1L), any())).thenReturn(Optional.empty());

        timesheetService.recordLogout("emp@company.com");

        verify(sessionRepository, never()).save(any());
    }
}
