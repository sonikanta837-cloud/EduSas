package com.emp.management.service;

import com.emp.management.dto.JobDailySummaryDTO;
import com.emp.management.entity.*;
import com.emp.management.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobDailySummaryService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final int REQUIRED_MINUTES = 8 * 60;

    private final JobDailySummaryRepository summaryRepository;
    private final JobWorkSessionRepository sessionRepository;
    private final JobSessionBreakRepository breakRepository;
    private final EmployeeDetailsRepository employeeDetailsRepository;
    private final UserRepository userRepository;
    private final LeaveRepository leaveRepository;
    private final HolidayService holidayService;
    private final EmailService emailService;
    private final UnderHoursAlertLogRepository underHoursAlertLogRepository;

    // ── Daily rollup — runs at 23:55 IST (deliberately not midnight; the host
    //    can sleep at exact midnight, matching the existing DailyWorkReportService
    //    / runMissingLogoutDetection precedent in this codebase) ────────────────

    @Scheduled(cron = "0 ${app.scheduler.job-summary.minute:55} ${app.scheduler.job-summary.hour:23} * * *",
            zone = "Asia/Kolkata")
    @Transactional
    public void generateDailySummaries() {
        LocalDate today = LocalDate.now(ZONE);
        log.info("Generating job daily summaries for {}", today);
        int generated = 0;

        for (EmployeeDetails emp : employeeDetailsRepository.findByActive(true)) {
            try {
                generateForEmployee(emp, today);
                generated++;
            } catch (Exception e) {
                log.warn("Job daily summary generation failed for employee {}: {}", emp.getFullName(), e.getMessage());
            }
        }
        log.info("Job daily summary generation completed for {} — {} employee(s) processed", today, generated);
    }

    // ── On-demand backfill/regenerate for a date range (admin/HR triggered) ───

    @Transactional
    public void generateAllForRange(LocalDate start, LocalDate end) {
        log.info("On-demand job summary generation triggered for range {} to {}", start, end);
        LocalDate current = start;
        while (!current.isAfter(end)) {
            final LocalDate date = current;
            for (EmployeeDetails emp : employeeDetailsRepository.findByActive(true)) {
                try {
                    generateForEmployee(emp, date);
                } catch (Exception e) {
                    log.warn("Job summary generation failed for {} on {}: {}", emp.getFullName(), date, e.getMessage());
                }
            }
            current = current.plusDays(1);
        }
        log.info("Completed job summary generation for range {} to {}", start, end);
    }

    @Transactional
    public JobDailySummary generateForEmployee(EmployeeDetails emp, LocalDate date) {
        List<JobWorkSession> sessions = sessionRepository
                .findByEmployeeIdAndWorkDateOrderByLoginTimeAsc(emp.getId(), date);
        Computed c = computeSummary(emp, date, sessions, LocalDateTime.now(ZONE));

        JobDailySummary summary = summaryRepository.findByEmployeeIdAndWorkDate(emp.getId(), date)
                .orElse(JobDailySummary.builder().employee(emp).workDate(date).build());
        summary.setTotalWorkingMinutes(c.totalWorkingMinutes());
        summary.setTotalBreakMinutes(c.totalBreakMinutes());
        summary.setSessionCount(c.sessionCount());
        summary.setFirstLoginTime(c.firstLoginTime());
        summary.setLastLogoutTime(c.lastLogoutTime());
        summary.setPrimaryClient(c.primaryClient());
        summary.setStatus(c.status());
        summary.setComputedAt(LocalDateTime.now(ZONE));
        return summaryRepository.save(summary);
    }

    // ── Pure computation — no persistence. Shared by the nightly rollup, the
    //    live "today" read paths below, and the dashboard's live org-wide
    //    aggregate, so classification logic only exists in one place. ────────

    private Computed computeSummary(EmployeeDetails emp, LocalDate date, List<JobWorkSession> sessions, LocalDateTime asOf) {
        DayOfWeek dow = date.getDayOfWeek();
        Set<LocalDate> holidays = holidayService.getApplicableHolidayDates(
                date, date, emp.getSeatingLocation() != null ? emp.getSeatingLocation() : "ALL", emp.getDepartment());

        int sessionCount = sessions.size();
        Map<Long, Integer> breakMinutesBySession = breakMinutesBySession(sessions, asOf);
        int totalWorkingMinutes = sessions.stream()
                .mapToInt(s -> minutesFor(s, asOf, breakMinutesBySession.getOrDefault(s.getId(), 0))).sum();
        int totalBreakMinutes = breakMinutesBySession.values().stream().mapToInt(Integer::intValue).sum();

        LocalDateTime firstLoginTime = sessions.stream()
                .map(JobWorkSession::getLoginTime).filter(Objects::nonNull)
                .min(LocalDateTime::compareTo).orElse(null);
        LocalDateTime lastLogoutTime = sessions.stream()
                .map(JobWorkSession::getLogoutTime).filter(Objects::nonNull)
                .max(LocalDateTime::compareTo).orElse(null);
        Set<String> distinctClients = sessions.stream()
                .map(s -> s.getClient() != null ? s.getClient().getValue() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        String primaryClient = distinctClients.isEmpty() ? null
                : distinctClients.size() == 1 ? distinctClients.iterator().next() : "Multiple";

        // Holiday takes precedence over weekend so a public holiday that falls on a
        // Saturday/Sunday still surfaces as "Holiday" (e.g. shows the holiday name)
        // rather than being masked as a generic weekly off.
        DailyAttendanceStatus status;
        if (holidays.contains(date)) {
            status = DailyAttendanceStatus.HOLIDAY;
        } else if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            status = DailyAttendanceStatus.WEEKEND;
        } else if (sessionCount == 0) {
            status = leaveRepository.hasLeaveOnDate(emp.getId(), date, LeaveStatus.APPROVED)
                    ? DailyAttendanceStatus.LEAVE : DailyAttendanceStatus.ABSENT;
        } else if (totalWorkingMinutes < REQUIRED_MINUTES) {
            status = DailyAttendanceStatus.UNDER_HOURS;
        } else if (totalWorkingMinutes > REQUIRED_MINUTES) {
            status = DailyAttendanceStatus.OVERTIME;
        } else {
            status = DailyAttendanceStatus.PRESENT;
        }

        return new Computed(totalWorkingMinutes, totalBreakMinutes, sessionCount,
                firstLoginTime, lastLogoutTime, primaryClient, status);
    }

    // Session still open (no logout yet) counts its elapsed minutes so "today" reads
    // live instead of showing 0 until the employee clocks out. Any break time taken
    // *within* this session is carved back out — the raw clocked duration (login to
    // logout, or login to now) includes time spent on break, so working minutes must
    // exclude it or office time (working + break) would double-count that time.
    private int minutesFor(JobWorkSession s, LocalDateTime asOf, int breakMinutesWithinSession) {
        int raw;
        if (s.getSessionMinutes() != null) {
            raw = s.getSessionMinutes();
        } else if (s.getLogoutTime() == null && s.getLoginTime() != null) {
            raw = (int) ChronoUnit.MINUTES.between(s.getLoginTime(), asOf);
        } else {
            raw = 0;
        }
        return Math.max(0, raw - breakMinutesWithinSession);
    }

    // Break time is sourced directly from the Job Time Tracking "Start Break / End
    // Break" records (JobSessionBreak) for the day's sessions — not inferred from
    // gaps between sessions, which could just as easily be idle time between
    // switching jobs rather than an actual break. Grouped by session so the caller
    // can also subtract each session's break time out of its raw clocked duration.
    private Map<Long, Integer> breakMinutesBySession(List<JobWorkSession> sessions, LocalDateTime asOf) {
        if (sessions.isEmpty()) return Map.of();
        List<Long> sessionIds = sessions.stream().map(JobWorkSession::getId).collect(Collectors.toList());
        List<JobSessionBreak> breaks = breakRepository.findByJobWorkSessionIdIn(sessionIds);

        Map<Long, Integer> result = new HashMap<>();
        for (JobSessionBreak b : breaks) {
            int minutes;
            if (b.getBreakMinutes() != null) {
                minutes = b.getBreakMinutes();
            } else if (b.getBreakEndTime() == null && b.getBreakStartTime() != null) {
                // Break still in progress — count elapsed so far so "today" ticks live.
                minutes = Math.max(0, (int) ChronoUnit.MINUTES.between(b.getBreakStartTime(), asOf));
            } else {
                minutes = 0;
            }
            Long sessionId = b.getJobWorkSession().getId();
            result.merge(sessionId, minutes, Integer::sum);
        }
        return result;
    }

    private record Computed(int totalWorkingMinutes, int totalBreakMinutes, int sessionCount,
                             LocalDateTime firstLoginTime, LocalDateTime lastLogoutTime,
                             String primaryClient, DailyAttendanceStatus status) {}

    // ── Consolidated under-hours audit email — 11:00 AM working days ──────────

    @Scheduled(cron = "0 ${app.scheduler.under-hours-audit.minute:0} ${app.scheduler.under-hours-audit.hour:11} * * *",
            zone = "Asia/Kolkata")
    @Transactional
    public void runUnderHoursAuditEmail() {
        LocalDate auditDate = LocalDate.now(ZONE).minusDays(1);
        DayOfWeek dow = auditDate.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            log.info("Under-hours audit skipped — {} is a weekend", auditDate);
            return;
        }

        List<JobDailySummary> underHours = summaryRepository
                .findByWorkDateAndStatusAndUnderHoursAlertSentFalse(auditDate, DailyAttendanceStatus.UNDER_HOURS);

        // Group eligible summaries by resolved manager email
        Map<String, List<JobDailySummary>> byManager = new LinkedHashMap<>();

        for (JobDailySummary summary : underHours) {
            EmployeeDetails emp = summary.getEmployee();
            if (emp.getUser() == null) continue;

            // Exclude: approved leave covering this date
            if (leaveRepository.hasLeaveOnDate(emp.getId(), auditDate, LeaveStatus.APPROVED)) {
                markSent(summary);
                continue;
            }

            String managerEmail = (emp.getManager() != null && emp.getManager().getUser() != null)
                    ? emp.getManager().getUser().getEmail() : null;
            if (managerEmail == null) {
                managerEmail = userRepository.findByRoleIn(List.of(Role.ADMIN, Role.DIRECTOR)).stream()
                        .map(User::getEmail)
                        .filter(e -> e != null && !e.isBlank())
                        .findFirst().orElse(null);
            }
            if (managerEmail == null) {
                log.warn("No manager or admin found for under-hours audit — employee: {}", emp.getFullName());
                continue;
            }

            byManager.computeIfAbsent(managerEmail, k -> new java.util.ArrayList<>()).add(summary);
        }

        int emailsSent = 0;
        for (Map.Entry<String, List<JobDailySummary>> entry : byManager.entrySet()) {
            String managerEmail = entry.getKey();
            List<JobDailySummary> rows = entry.getValue();

            String[] cc = Stream.concat(
                    userRepository.findByRole(Role.HR).stream(),
                    userRepository.findByRoleIn(List.of(Role.ADMIN, Role.DIRECTOR)).stream()
            )
            .map(User::getEmail)
            .filter(e -> e != null && !e.isBlank() && !e.equalsIgnoreCase(managerEmail))
            .distinct()
            .toArray(String[]::new);

            DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("hh:mm a");
            DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd MMM yyyy");
            LocalDateTime sentAt = LocalDateTime.now(ZONE);

            List<EmailService.UnderHoursRow> emailRows = new java.util.ArrayList<>();
            for (JobDailySummary s : rows) {
                EmployeeDetails emp = s.getEmployee();
                int shortfallMinutes = Math.max(0, REQUIRED_MINUTES - s.getTotalWorkingMinutes());

                emailRows.add(new EmailService.UnderHoursRow(
                        emp.getEmployeeCode(),
                        emp.getFullName(),
                        emp.getDepartment(),
                        s.getPrimaryClient(),
                        formatMinutes(s.getTotalWorkingMinutes()),
                        formatMinutes(s.getTotalBreakMinutes()),
                        "8h 0m",
                        formatMinutes(shortfallMinutes),
                        auditDate.format(dateFmt),
                        s.getFirstLoginTime() != null ? s.getFirstLoginTime().format(timeFmt) : null,
                        s.getLastLogoutTime() != null ? s.getLastLogoutTime().format(timeFmt) : null,
                        s.getStatus().name()));

                // Audit trail — one row per employee actually included in this notification,
                // independent of the underHoursAlertSent guard flag which only prevents resend.
                underHoursAlertLogRepository.save(UnderHoursAlertLog.builder()
                        .employee(emp)
                        .workDate(auditDate)
                        .employeeName(emp.getFullName())
                        .employeeCode(emp.getEmployeeCode())
                        .department(emp.getDepartment())
                        .client(s.getPrimaryClient())
                        .totalWorkingMinutes(s.getTotalWorkingMinutes())
                        .totalBreakMinutes(s.getTotalBreakMinutes())
                        .expectedMinutes(REQUIRED_MINUTES)
                        .shortfallMinutes(shortfallMinutes)
                        .firstLoginTime(s.getFirstLoginTime())
                        .lastLogoutTime(s.getLastLogoutTime())
                        .attendanceStatus(s.getStatus().name())
                        .recipientEmail(managerEmail)
                        .sentAt(sentAt)
                        .build());
            }

            emailService.sendUnderHoursAuditAlert(managerEmail, cc, auditDate, emailRows);
            emailsSent++;

            rows.forEach(this::markSent);
        }

        log.info("Under-hours audit completed for {} — {} manager email(s) sent", auditDate, emailsSent);
    }

    private void markSent(JobDailySummary summary) {
        summary.setUnderHoursAlertSent(true);
        summaryRepository.save(summary);
    }

    private String formatMinutes(int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;
        return h + "h " + m + "m";
    }

    // ── Read endpoints ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<JobDailySummaryDTO> getRange(Long employeeId, LocalDate start, LocalDate end) {
        LocalDate today = LocalDate.now(ZONE);

        // Today's persisted row (if any) is never trusted — Job Time Tracking activity can
        // happen any time during the day, so a row written earlier this morning (e.g. by the
        // nightly rollup running against a manipulated/fast-forwarded clock, or a stale
        // ABSENT row from before the employee clocked in) would otherwise permanently mask
        // real sessions created later today. Today is always recomputed live instead.
        List<JobDailySummary> persisted = summaryRepository
                .findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(employeeId, start, end)
                .stream().filter(s -> !s.getWorkDate().equals(today)).collect(Collectors.toList());
        List<JobDailySummaryDTO> result = persisted.stream().map(this::toDTO).collect(Collectors.toList());

        boolean todayInRange = !today.isBefore(start) && !today.isAfter(end);
        if (todayInRange) {
            employeeDetailsRepository.findById(employeeId)
                    .ifPresent(emp -> result.add(liveForEmployeeDate(emp, today)));
        }
        result.sort(Comparator.comparing(JobDailySummaryDTO::getWorkDate));
        return result;
    }

    @Transactional(readOnly = true)
    public JobDailySummaryDTO getForDate(Long employeeId, LocalDate date) {
        // See getRange — today is always live-computed, never read from a persisted row.
        if (date.equals(LocalDate.now(ZONE))) {
            return employeeDetailsRepository.findById(employeeId)
                    .map(emp -> liveForEmployeeDate(emp, date)).orElse(null);
        }
        return summaryRepository.findByEmployeeIdAndWorkDate(employeeId, date)
                .map(this::toDTO).orElse(null);
    }

    // ── Team / org-wide range (role-scoped: admin/HR see everyone, managers
    //    see their direct reports) — backs the Attendance Reports page ────────

    @Transactional(readOnly = true)
    public List<JobDailySummaryDTO> getTeamRange(String email, LocalDate start, LocalDate end) {
        EmployeeDetails requester = employeeDetailsRepository.findByUserEmail(email).orElse(null);
        if (requester == null) return List.of();

        User user = requester.getUser();
        boolean isAdminOrHr = user != null &&
                (user.getRole() == Role.ADMIN || user.getRole() == Role.DIRECTOR || user.getRole() == Role.HR);

        LocalDate today = LocalDate.now(ZONE);

        // Today is never trusted from persisted rows — see getRange for why.
        List<JobDailySummary> persisted = (isAdminOrHr
                ? summaryRepository.findAllForReport(start, end)
                : summaryRepository.findByManagerForReport(requester.getId(), start, end))
                .stream().filter(s -> !s.getWorkDate().equals(today)).collect(Collectors.toList());

        List<JobDailySummaryDTO> result = persisted.stream().map(this::toDTO).collect(Collectors.toList());

        if (!today.isBefore(start) && !today.isAfter(end)) {
            List<EmployeeDetails> scope = isAdminOrHr
                    ? employeeDetailsRepository.findByActive(true)
                    : employeeDetailsRepository.findByActive(true).stream()
                        .filter(e -> e.getManager() != null && requester.getId().equals(e.getManager().getId()))
                        .collect(Collectors.toList());

            result.addAll(liveSummariesFor(scope, today));
        }

        result.sort(Comparator.comparing(JobDailySummaryDTO::getWorkDate).thenComparing(JobDailySummaryDTO::getEmployeeName));
        return result;
    }

    // ── Live org-wide "today" aggregate — feeds the Dashboard, since the
    //    nightly rollup for today doesn't exist until 23:55 ───────────────────

    @Transactional(readOnly = true)
    public List<JobDailySummaryDTO> getTodayLiveSummaries() {
        return liveSummariesFor(employeeDetailsRepository.findByActive(true), LocalDate.now(ZONE));
    }

    private List<JobDailySummaryDTO> liveSummariesFor(List<EmployeeDetails> employees, LocalDate date) {
        List<JobWorkSession> sessions = sessionRepository.findAllByWorkDate(date);
        Map<Long, List<JobWorkSession>> byEmployee = sessions.stream()
                .collect(Collectors.groupingBy(s -> s.getEmployee().getId()));
        LocalDateTime now = LocalDateTime.now(ZONE);
        List<JobDailySummaryDTO> result = new ArrayList<>();
        for (EmployeeDetails emp : employees) {
            List<JobWorkSession> empSessions = byEmployee.getOrDefault(emp.getId(), List.of());
            result.add(toLiveDTO(emp, date, computeSummary(emp, date, empSessions, now)));
        }
        return result;
    }

    private JobDailySummaryDTO liveForEmployeeDate(EmployeeDetails emp, LocalDate date) {
        List<JobWorkSession> sessions = sessionRepository
                .findByEmployeeIdAndWorkDateOrderByLoginTimeAsc(emp.getId(), date);
        return toLiveDTO(emp, date, computeSummary(emp, date, sessions, LocalDateTime.now(ZONE)));
    }

    // ── CSV export ────────────────────────────────────────────────────────────

    public byte[] exportToCsv(String email, LocalDate start, LocalDate end) {
        List<JobDailySummaryDTO> rows = getTeamRange(email, start, end);
        StringBuilder sb = new StringBuilder();
        sb.append("Employee,Employee Code,Department,Date,First Login,Last Logout," +
                  "Working Hours,Break Time,Office Time,Overtime,Status,Sessions\n");
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
        for (JobDailySummaryDTO r : rows) {
            sb.append(String.join(",",
                    csvEscape(r.getEmployeeName()),
                    csvEscape(r.getEmployeeCode()),
                    csvEscape(r.getDepartment()),
                    r.getWorkDate().toString(),
                    r.getFirstLoginTime() != null ? r.getFirstLoginTime().toLocalTime().format(timeFmt) : "",
                    r.getLastLogoutTime() != null ? r.getLastLogoutTime().toLocalTime().format(timeFmt) : "",
                    csvEscape(formatMinutes(r.getTotalWorkingMinutes())),
                    csvEscape(formatMinutes(r.getTotalBreakMinutes())),
                    csvEscape(formatMinutes(r.getTotalOfficeMinutes())),
                    csvEscape(formatMinutes(r.getOvertimeMinutes())),
                    csvEscape(r.getStatus()),
                    String.valueOf(r.getSessionCount())
            )).append("\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n"))
            return "\"" + value.replace("\"", "\"\"") + "\"";
        return value;
    }

    // ── DTO mapping ───────────────────────────────────────────────────────────

    private JobDailySummaryDTO toDTO(JobDailySummary s) {
        Computed c = new Computed(s.getTotalWorkingMinutes(), s.getTotalBreakMinutes(), s.getSessionCount(),
                s.getFirstLoginTime(), s.getLastLogoutTime(), s.getPrimaryClient(), s.getStatus());
        return buildDTO(s.getEmployee(), s.getWorkDate(), c);
    }

    private JobDailySummaryDTO toLiveDTO(EmployeeDetails emp, LocalDate date, Computed c) {
        return buildDTO(emp, date, c);
    }

    private JobDailySummaryDTO buildDTO(EmployeeDetails emp, LocalDate date, Computed c) {
        return JobDailySummaryDTO.builder()
                .employeeId(emp.getId())
                .employeeName(emp.getFullName())
                .employeeCode(emp.getEmployeeCode())
                .department(emp.getDepartment())
                .workDate(date)
                .totalWorkingMinutes(c.totalWorkingMinutes())
                .totalBreakMinutes(c.totalBreakMinutes())
                .totalOfficeMinutes(c.totalWorkingMinutes() + c.totalBreakMinutes())
                .overtimeMinutes(Math.max(0, c.totalWorkingMinutes() - REQUIRED_MINUTES))
                .sessionCount(c.sessionCount())
                .firstLoginTime(c.firstLoginTime())
                .lastLogoutTime(c.lastLogoutTime())
                .primaryClient(c.primaryClient())
                .status(c.status().name())
                .build();
    }
}
