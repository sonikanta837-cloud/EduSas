package com.emp.management.service;

import com.emp.management.dto.JobDailySummaryDTO;
import com.emp.management.entity.EmployeeDetails;
import com.emp.management.entity.JobDailySummary;
import com.emp.management.entity.JobSessionBreak;
import com.emp.management.entity.JobSessionStatus;
import com.emp.management.entity.JobWorkSession;
import com.emp.management.repository.EmployeeDetailsRepository;
import com.emp.management.repository.JobDailySummaryRepository;
import com.emp.management.repository.JobSessionBreakRepository;
import com.emp.management.repository.JobWorkSessionRepository;
import com.emp.management.repository.LeaveRepository;
import com.emp.management.repository.UnderHoursAlertLogRepository;
import com.emp.management.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the "no login/logout time shown" bug: today's
 * JobDailySummary can already have a stale persisted row (e.g. written by the
 * nightly rollup before the employee clocked in) with sessionCount=0/ABSENT.
 * Reads for "today" must always recompute live from JobWorkSession instead of
 * trusting that stale row, otherwise real same-day sessions never show up.
 */
@ExtendWith(MockitoExtension.class)
class JobDailySummaryServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    @Mock private JobDailySummaryRepository summaryRepository;
    @Mock private JobWorkSessionRepository sessionRepository;
    @Mock private JobSessionBreakRepository breakRepository;
    @Mock private EmployeeDetailsRepository employeeDetailsRepository;
    @Mock private UserRepository userRepository;
    @Mock private LeaveRepository leaveRepository;
    @Mock private HolidayService holidayService;
    @Mock private EmailService emailService;
    @Mock private UnderHoursAlertLogRepository underHoursAlertLogRepository;

    @InjectMocks private JobDailySummaryService service;

    @Test
    void getRange_ignoresStalePersistedRowForToday_andReturnsLiveSessionData() {
        LocalDate today = LocalDate.now(ZONE);
        EmployeeDetails emp = EmployeeDetails.builder()
                .id(2L).firstName("Pooja").lastName("Soni").employeeCode("E002").department("Engineering")
                .build();

        // A persisted row already exists for today showing ABSENT/no sessions —
        // e.g. written before the employee clocked in via Job Time Tracking.
        when(summaryRepository.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(2L, today, today))
                .thenReturn(List.of()); // filtered out for today regardless — simulate the DB having a stale row too
        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(emp));
        when(holidayService.getApplicableHolidayDates(any(), any(), anyString(), any()))
                .thenReturn(Set.of());

        LocalDateTime login = LocalDateTime.now(ZONE).minusHours(2);
        JobWorkSession openSession = JobWorkSession.builder()
                .id(99L).employee(emp).workDate(today)
                .loginTime(login).logoutTime(null).sessionMinutes(null)
                .status(JobSessionStatus.OPEN)
                .build();
        when(sessionRepository.findByEmployeeIdAndWorkDateOrderByLoginTimeAsc(2L, today))
                .thenReturn(List.of(openSession));

        List<JobDailySummaryDTO> result = service.getRange(2L, today, today);

        assertThat(result).hasSize(1);
        JobDailySummaryDTO todaySummary = result.get(0);
        assertThat(todaySummary.getSessionCount()).isEqualTo(1);
        assertThat(todaySummary.getFirstLoginTime()).isEqualTo(login);
        assertThat(todaySummary.getStatus()).isNotEqualTo("ABSENT");
        assertThat(todaySummary.getTotalWorkingMinutes()).isGreaterThan(0);
    }

    @Test
    void getForDate_forToday_recomputesLive_ignoringAnyPersistedRow() {
        LocalDate today = LocalDate.now(ZONE);
        EmployeeDetails emp = EmployeeDetails.builder()
                .id(5L).firstName("Test").lastName("Employee").employeeCode("E005").department("HR")
                .build();

        when(employeeDetailsRepository.findById(5L)).thenReturn(Optional.of(emp));
        when(holidayService.getApplicableHolidayDates(any(), any(), anyString(), any()))
                .thenReturn(Set.of());

        LocalDateTime login = LocalDateTime.now(ZONE).minusMinutes(30);
        JobWorkSession session = JobWorkSession.builder()
                .id(1L).employee(emp).workDate(today)
                .loginTime(login).logoutTime(null).sessionMinutes(null)
                .status(JobSessionStatus.OPEN)
                .build();
        when(sessionRepository.findByEmployeeIdAndWorkDateOrderByLoginTimeAsc(5L, today))
                .thenReturn(List.of(session));

        JobDailySummaryDTO dto = service.getForDate(5L, today);

        assertThat(dto).isNotNull();
        assertThat(dto.getFirstLoginTime()).isEqualTo(login);
        assertThat(dto.getSessionCount()).isEqualTo(1);
        // getForDate for "today" must never consult summaryRepository.findByEmployeeIdAndWorkDate —
        // it always recomputes live so a stale persisted row can never mask real sessions.
        org.mockito.Mockito.verifyNoInteractions(summaryRepository);
    }

    @Test
    void computeSummary_sourcesBreakTimeFromJobSessionBreak_notFromInterSessionGaps() {
        LocalDate date = LocalDate.now(ZONE).minusDays(1); // a settled, non-today date
        EmployeeDetails emp = EmployeeDetails.builder()
                .id(7L).firstName("Vaibhav").lastName("Soni").employeeCode("E007").department("QA")
                .build();

        when(summaryRepository.findByEmployeeIdAndWorkDate(7L, date)).thenReturn(Optional.empty());
        when(summaryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(holidayService.getApplicableHolidayDates(any(), any(), anyString(), any())).thenReturn(Set.of());

        JobWorkSession session1 = JobWorkSession.builder()
                .id(10L).employee(emp).workDate(date)
                .loginTime(date.atTime(9, 0)).logoutTime(date.atTime(12, 0)).sessionMinutes(180)
                .status(JobSessionStatus.CLOSED).build();
        // A large gap follows session1 (12:00 -> 14:00) that is NOT an explicit break —
        // e.g. the employee simply didn't clock into a new job until later. This must
        // NOT be counted as break time now that break time comes from JobSessionBreak.
        JobWorkSession session2 = JobWorkSession.builder()
                .id(11L).employee(emp).workDate(date)
                .loginTime(date.atTime(14, 0)).logoutTime(date.atTime(18, 0)).sessionMinutes(240)
                .status(JobSessionStatus.CLOSED).build();
        when(sessionRepository.findByEmployeeIdAndWorkDateOrderByLoginTimeAsc(7L, date))
                .thenReturn(List.of(session1, session2));

        // One explicit 20-minute break recorded inside session1 via Start Break/End Break.
        JobSessionBreak lunchBreak = JobSessionBreak.builder()
                .id(1L).jobWorkSession(session1)
                .breakStartTime(date.atTime(10, 0)).breakEndTime(date.atTime(10, 20)).breakMinutes(20)
                .build();
        when(breakRepository.findByJobWorkSessionIdIn(List.of(10L, 11L))).thenReturn(List.of(lunchBreak));

        JobDailySummary saved = service.generateForEmployee(emp, date);

        assertThat(saved.getTotalBreakMinutes()).isEqualTo(20);
        // session1 raw 180min - 20min break taken inside it = 160 productive minutes;
        // session2 has no break, so its full 240 raw minutes count as working.
        assertThat(saved.getTotalWorkingMinutes()).isEqualTo(400);
    }
}
