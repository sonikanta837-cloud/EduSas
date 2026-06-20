package com.emp.management.service;

import com.emp.management.dto.DailyWorkReportDTO;
import com.emp.management.entity.*;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DailyWorkReportServiceTest {

    @Mock private DailyWorkReportRepository reportRepository;
    @Mock private AttendanceSessionRepository sessionRepository;
    @Mock private EmployeeDetailsRepository employeeDetailsRepository;
    @Mock private UserRepository userRepository;
    @Mock private EmailService emailService;
    @Mock private SystemSettingService systemSettingService;

    @InjectMocks private DailyWorkReportService dailyWorkReportService;

    private EmployeeDetails adminEmp;
    private EmployeeDetails regularEmp;
    private EmployeeDetails managerEmp;
    private DailyWorkReport sampleReport;

    @BeforeEach
    void setUp() {
        User adminUser = User.builder().id(1L).email("admin@company.com").role(Role.ADMIN).active(true).build();
        adminEmp = EmployeeDetails.builder().id(1L).user(adminUser)
                .firstName("Admin").lastName("User").employeeCode("EMP10001").active(true).build();
        adminUser.setEmployeeDetails(adminEmp);

        User mgrUser = User.builder().id(2L).email("mgr@company.com").role(Role.MANAGER).active(true).build();
        managerEmp = EmployeeDetails.builder().id(2L).user(mgrUser)
                .firstName("Jane").lastName("Manager").employeeCode("EMP10002").active(true).build();
        mgrUser.setEmployeeDetails(managerEmp);

        User empUser = User.builder().id(3L).email("emp@company.com").role(Role.EMPLOYEE).active(true).build();
        regularEmp = EmployeeDetails.builder().id(3L).user(empUser)
                .firstName("John").lastName("Doe").employeeCode("EMP10003")
                .manager(managerEmp).active(true).build();
        empUser.setEmployeeDetails(regularEmp);

        sampleReport = DailyWorkReport.builder()
                .id(1L).employee(regularEmp)
                .reportDate(LocalDate.of(2026, 6, 1))
                .firstLoginTime(LocalTime.of(9, 0))
                .lastLogoutTime(LocalTime.of(18, 0))
                .totalOfficeMinutes(540)
                .activeMinutes(480)
                .breakMinutes(60)
                .sessionCount(2)
                .build();
    }

    // ── generateReportForEmployee ─────────────────────────────────────────────

    @Test
    void generateReportForEmployee_withCompletedSessions_createsReport() {
        LocalDate date = LocalDate.of(2026, 6, 1);

        AttendanceSession session1 = AttendanceSession.builder()
                .id(1L).employee(regularEmp)
                .workDate(date)
                .loginTime(LocalTime.of(9, 0))
                .logoutTime(LocalTime.of(12, 0))
                .build();
        AttendanceSession session2 = AttendanceSession.builder()
                .id(2L).employee(regularEmp)
                .workDate(date)
                .loginTime(LocalTime.of(13, 0))
                .logoutTime(LocalTime.of(18, 0))
                .build();

        when(sessionRepository.findByEmployeeIdAndWorkDateOrderByLoginTimeAsc(3L, date))
                .thenReturn(List.of(session1, session2));
        when(reportRepository.findByEmployeeIdAndReportDate(3L, date)).thenReturn(Optional.empty());
        when(reportRepository.save(any())).thenReturn(sampleReport);

        DailyWorkReport result = dailyWorkReportService.generateReportForEmployee(regularEmp, date);

        assertThat(result).isNotNull();
        verify(reportRepository).save(any(DailyWorkReport.class));
    }

    @Test
    void generateReportForEmployee_updatesExistingReport() {
        LocalDate date = LocalDate.of(2026, 6, 1);

        AttendanceSession session = AttendanceSession.builder()
                .id(1L).employee(regularEmp)
                .workDate(date)
                .loginTime(LocalTime.of(9, 0))
                .logoutTime(LocalTime.of(18, 0))
                .build();

        when(sessionRepository.findByEmployeeIdAndWorkDateOrderByLoginTimeAsc(3L, date))
                .thenReturn(List.of(session));
        when(reportRepository.findByEmployeeIdAndReportDate(3L, date))
                .thenReturn(Optional.of(sampleReport));
        when(reportRepository.save(any())).thenReturn(sampleReport);

        DailyWorkReport result = dailyWorkReportService.generateReportForEmployee(regularEmp, date);

        assertThat(result).isNotNull();
        // Verify it updated the existing report (not a new one)
        verify(reportRepository).save(sampleReport);
    }

    @Test
    void generateReportForEmployee_noCompletedSessions_returnsNull() {
        LocalDate date = LocalDate.of(2026, 6, 1);

        // Session without logout time — incomplete
        AttendanceSession incomplete = AttendanceSession.builder()
                .id(1L).employee(regularEmp)
                .workDate(date)
                .loginTime(LocalTime.of(9, 0))
                .logoutTime(null)
                .build();

        when(sessionRepository.findByEmployeeIdAndWorkDateOrderByLoginTimeAsc(3L, date))
                .thenReturn(List.of(incomplete));

        DailyWorkReport result = dailyWorkReportService.generateReportForEmployee(regularEmp, date);

        assertThat(result).isNull();
        verify(reportRepository, never()).save(any());
    }

    @Test
    void generateReportForEmployee_emptySessions_returnsNull() {
        LocalDate date = LocalDate.of(2026, 6, 1);

        when(sessionRepository.findByEmployeeIdAndWorkDateOrderByLoginTimeAsc(3L, date))
                .thenReturn(List.of());

        DailyWorkReport result = dailyWorkReportService.generateReportForEmployee(regularEmp, date);

        assertThat(result).isNull();
    }

    @Test
    void generateReportForEmployee_singleSession_calculatesCorrectly() {
        LocalDate date = LocalDate.of(2026, 6, 2);

        AttendanceSession session = AttendanceSession.builder()
                .id(1L).employee(regularEmp)
                .workDate(date)
                .loginTime(LocalTime.of(9, 0))
                .logoutTime(LocalTime.of(17, 0))
                .build();

        when(sessionRepository.findByEmployeeIdAndWorkDateOrderByLoginTimeAsc(3L, date))
                .thenReturn(List.of(session));
        when(reportRepository.findByEmployeeIdAndReportDate(3L, date)).thenReturn(Optional.empty());
        when(reportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DailyWorkReport result = dailyWorkReportService.generateReportForEmployee(regularEmp, date);

        assertThat(result).isNotNull();
        // totalOffice = 8 hours = 480 minutes
        assertThat(result.getTotalOfficeMinutes()).isEqualTo(480);
        // With single session, activeMinutes == totalOfficeMinutes (no breaks)
        assertThat(result.getActiveMinutes()).isEqualTo(480);
        assertThat(result.getBreakMinutes()).isEqualTo(0);
        assertThat(result.getSessionCount()).isEqualTo(1);
    }

    // ── generateAllReportsForDate ─────────────────────────────────────────────

    @Test
    void generateAllReportsForDate_activeEmployees_generatesReports() {
        LocalDate date = LocalDate.of(2026, 6, 2); // Monday

        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(regularEmp));
        when(sessionRepository.findByEmployeeIdAndWorkDateOrderByLoginTimeAsc(3L, date))
                .thenReturn(List.of());  // no sessions — returns null

        dailyWorkReportService.generateAllReportsForDate(date);

        verify(sessionRepository).findByEmployeeIdAndWorkDateOrderByLoginTimeAsc(3L, date);
    }

    @Test
    void generateAllReportsForDate_employeeWithNullUser_skipped() {
        LocalDate date = LocalDate.of(2026, 6, 2);

        EmployeeDetails empNoUser = EmployeeDetails.builder().id(99L).user(null).active(true).build();
        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(empNoUser));

        // Should not throw — skips employees with null user
        dailyWorkReportService.generateAllReportsForDate(date);

        verify(sessionRepository, never()).findByEmployeeIdAndWorkDateOrderByLoginTimeAsc(anyLong(), any());
    }

    // ── generateAllReportsForRange ────────────────────────────────────────────

    @Test
    void generateAllReportsForRange_weekdaysOnly_skipWeekends() {
        // June 1 2026 = Monday, June 7 = Sunday
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end   = LocalDate.of(2026, 6, 7);

        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(regularEmp));
        when(sessionRepository.findByEmployeeIdAndWorkDateOrderByLoginTimeAsc(anyLong(), any()))
                .thenReturn(List.of());

        dailyWorkReportService.generateAllReportsForRange(start, end);

        // 7 days: Mon-Fri = 5 weekdays, Sat-Sun = 2 weekends
        // Sessions queried 5 times (Mon-Fri), not on Sat-Sun
        verify(sessionRepository, times(5))
                .findByEmployeeIdAndWorkDateOrderByLoginTimeAsc(anyLong(), any());
    }

    // ── getTeamReports ────────────────────────────────────────────────────────

    @Test
    void getTeamReports_asAdmin_returnsAllReports() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end   = LocalDate.of(2026, 6, 30);

        when(employeeDetailsRepository.findByUserEmail("admin@company.com"))
                .thenReturn(Optional.of(adminEmp));
        when(reportRepository.findAllByDateRange(start, end)).thenReturn(List.of(sampleReport));

        List<DailyWorkReportDTO> result = dailyWorkReportService.getTeamReports("admin@company.com", start, end);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmployeeName()).isEqualTo("John Doe");
        verify(reportRepository).findAllByDateRange(start, end);
    }

    @Test
    void getTeamReports_asManager_returnsSubordinatesReports() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end   = LocalDate.of(2026, 6, 30);

        when(employeeDetailsRepository.findByUserEmail("mgr@company.com"))
                .thenReturn(Optional.of(managerEmp));
        when(reportRepository.findByManagerAndDateRange(2L, start, end)).thenReturn(List.of(sampleReport));

        List<DailyWorkReportDTO> result = dailyWorkReportService.getTeamReports("mgr@company.com", start, end);

        assertThat(result).hasSize(1);
        verify(reportRepository).findByManagerAndDateRange(2L, start, end);
    }

    @Test
    void getTeamReports_unknownUser_returnsEmptyList() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end   = LocalDate.of(2026, 6, 30);

        when(employeeDetailsRepository.findByUserEmail("ghost@company.com"))
                .thenReturn(Optional.empty());

        List<DailyWorkReportDTO> result = dailyWorkReportService.getTeamReports("ghost@company.com", start, end);

        assertThat(result).isEmpty();
    }

    // ── getMyReports ──────────────────────────────────────────────────────────

    @Test
    void getMyReports_returnsOwnReports() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end   = LocalDate.of(2026, 6, 30);

        when(employeeDetailsRepository.findByUserEmail("emp@company.com"))
                .thenReturn(Optional.of(regularEmp));
        when(reportRepository.findByEmployeeIdAndReportDateBetweenOrderByReportDateDesc(3L, start, end))
                .thenReturn(List.of(sampleReport));

        List<DailyWorkReportDTO> result = dailyWorkReportService.getMyReports("emp@company.com", start, end);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSessionCount()).isEqualTo(2);
    }

    @Test
    void getMyReports_unknownUser_returnsEmptyList() {
        when(employeeDetailsRepository.findByUserEmail("ghost@company.com"))
                .thenReturn(Optional.empty());

        List<DailyWorkReportDTO> result = dailyWorkReportService.getMyReports("ghost@company.com",
                LocalDate.now(), LocalDate.now());

        assertThat(result).isEmpty();
    }

    // ── exportToCsv ───────────────────────────────────────────────────────────

    @Test
    void exportToCsv_returnsValidCsvBytes() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end   = LocalDate.of(2026, 6, 30);

        when(employeeDetailsRepository.findByUserEmail("admin@company.com"))
                .thenReturn(Optional.of(adminEmp));
        when(reportRepository.findAllByDateRange(start, end)).thenReturn(List.of(sampleReport));

        byte[] csvBytes = dailyWorkReportService.exportToCsv("admin@company.com", start, end);

        assertThat(csvBytes).isNotEmpty();
        String csv = new String(csvBytes);
        assertThat(csv).contains("Employee,Employee Code");
        assertThat(csv).contains("John Doe");
    }

    @Test
    void exportToCsv_noReports_returnsHeaderOnly() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end   = LocalDate.of(2026, 6, 30);

        when(employeeDetailsRepository.findByUserEmail("admin@company.com"))
                .thenReturn(Optional.of(adminEmp));
        when(reportRepository.findAllByDateRange(start, end)).thenReturn(List.of());

        byte[] csvBytes = dailyWorkReportService.exportToCsv("admin@company.com", start, end);

        String csv = new String(csvBytes);
        assertThat(csv).contains("Employee,Employee Code");
        // Only header, no data rows
        assertThat(csv.lines().count()).isEqualTo(1L);
    }

    @Test
    void exportToCsv_nameWithComma_escapedInCsv() {
        LocalDate date = LocalDate.of(2026, 6, 1);

        // Employee with comma in name
        User user = User.builder().id(10L).email("x@company.com").role(Role.EMPLOYEE).active(true).build();
        EmployeeDetails empWithComma = EmployeeDetails.builder()
                .id(10L).user(user).firstName("Smith, Jr.").lastName("Johnson")
                .employeeCode("EMP10010").active(true).build();
        user.setEmployeeDetails(empWithComma);

        DailyWorkReport report = DailyWorkReport.builder()
                .id(5L).employee(empWithComma)
                .reportDate(date)
                .firstLoginTime(LocalTime.of(9, 0))
                .lastLogoutTime(LocalTime.of(18, 0))
                .totalOfficeMinutes(540).activeMinutes(480).breakMinutes(60)
                .sessionCount(1).build();

        when(employeeDetailsRepository.findByUserEmail("admin@company.com"))
                .thenReturn(Optional.of(adminEmp));
        when(reportRepository.findAllByDateRange(date, date)).thenReturn(List.of(report));

        byte[] csvBytes = dailyWorkReportService.exportToCsv("admin@company.com", date, date);
        String csv = new String(csvBytes);

        // Name with comma should be quoted
        assertThat(csv).contains("\"Smith, Jr. Johnson\"");
    }

    // ── fmtMinutes coverage ───────────────────────────────────────────────────

    @Test
    void getMyReports_nullMinutes_formattedAsEmpty() {
        LocalDate date = LocalDate.of(2026, 6, 1);

        DailyWorkReport nullMinutesReport = DailyWorkReport.builder()
                .id(2L).employee(regularEmp).reportDate(date)
                .totalOfficeMinutes(null).activeMinutes(null).breakMinutes(-1)
                .sessionCount(0).build();

        when(employeeDetailsRepository.findByUserEmail("emp@company.com"))
                .thenReturn(Optional.of(regularEmp));
        when(reportRepository.findByEmployeeIdAndReportDateBetweenOrderByReportDateDesc(3L, date, date))
                .thenReturn(List.of(nullMinutesReport));

        List<DailyWorkReportDTO> result = dailyWorkReportService.getMyReports("emp@company.com", date, date);

        assertThat(result).hasSize(1);
        // fmtMinutes(null) = "—"
        assertThat(result.get(0).getTotalOfficeFormatted()).isEqualTo("—");
        // fmtMinutes(-1) = "—" (negative)
        assertThat(result.get(0).getBreakFormatted()).isEqualTo("—");
    }
}
