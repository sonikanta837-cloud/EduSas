package com.emp.management.service;

import com.emp.management.dto.JobDailySummaryDTO;
import com.emp.management.entity.*;
import com.emp.management.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final EmployeeDetailsRepository employeeDetailsRepository;
    private final UserRepository userRepository;
    private final LeaveRepository leaveRepository;
    private final CorrectionRequestRepository correctionRequestRepository;
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

    @Transactional
    public JobDailySummary generateForEmployee(EmployeeDetails emp, LocalDate date) {
        DailyAttendanceStatus status;
        int totalWorkingMinutes = 0;
        int totalBreakMinutes = 0;
        int sessionCount = 0;

        DayOfWeek dow = date.getDayOfWeek();
        Set<LocalDate> holidays = holidayService.getApplicableHolidayDates(
                date, date, emp.getSeatingLocation() != null ? emp.getSeatingLocation() : "ALL", emp.getDepartment());

        List<JobWorkSession> sessions = sessionRepository
                .findByEmployeeIdAndWorkDateOrderByLoginTimeAsc(emp.getId(), date);
        sessionCount = sessions.size();
        totalWorkingMinutes = sessions.stream()
                .filter(s -> s.getSessionMinutes() != null)
                .mapToInt(JobWorkSession::getSessionMinutes)
                .sum();
        totalBreakMinutes = computeBreakMinutes(sessions);

        LocalDateTime firstLoginTime = sessions.stream()
                .map(JobWorkSession::getLoginTime).filter(java.util.Objects::nonNull)
                .min(LocalDateTime::compareTo).orElse(null);
        LocalDateTime lastLogoutTime = sessions.stream()
                .map(JobWorkSession::getLogoutTime).filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo).orElse(null);
        Set<String> distinctClients = sessions.stream()
                .map(s -> s.getClient() != null ? s.getClient().getValue() : null)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        String primaryClient = distinctClients.isEmpty() ? null
                : distinctClients.size() == 1 ? distinctClients.iterator().next() : "Multiple";

        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            status = DailyAttendanceStatus.WEEKEND;
        } else if (holidays.contains(date)) {
            status = DailyAttendanceStatus.HOLIDAY;
        } else if (sessionCount == 0) {
            status = DailyAttendanceStatus.ABSENT;
        } else if (totalWorkingMinutes >= REQUIRED_MINUTES) {
            status = DailyAttendanceStatus.COMPLETE;
        } else {
            status = DailyAttendanceStatus.UNDER_HOURS;
        }

        JobDailySummary summary = summaryRepository.findByEmployeeIdAndWorkDate(emp.getId(), date)
                .orElse(JobDailySummary.builder().employee(emp).workDate(date).build());
        summary.setTotalWorkingMinutes(totalWorkingMinutes);
        summary.setTotalBreakMinutes(totalBreakMinutes);
        summary.setSessionCount(sessionCount);
        summary.setFirstLoginTime(firstLoginTime);
        summary.setLastLogoutTime(lastLogoutTime);
        summary.setPrimaryClient(primaryClient);
        summary.setStatus(status);
        summary.setComputedAt(LocalDateTime.now(ZONE));
        return summaryRepository.save(summary);
    }

    private int computeBreakMinutes(List<JobWorkSession> sessions) {
        int total = 0;
        for (int i = 0; i < sessions.size() - 1; i++) {
            LocalDateTime logout = sessions.get(i).getLogoutTime();
            LocalDateTime nextLogin = sessions.get(i + 1).getLoginTime();
            if (logout != null && nextLogin != null && nextLogin.isAfter(logout)) {
                total += (int) java.time.temporal.ChronoUnit.MINUTES.between(logout, nextLogin);
            }
        }
        return total;
    }

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

            // Exclude: approved leave, or an approved correction covering this date
            if (leaveRepository.hasLeaveOnDate(emp.getId(), auditDate, LeaveStatus.APPROVED)) {
                markSent(summary);
                continue;
            }
            if (correctionRequestRepository.existsByEmployee_IdAndWorkDateAndStatus(
                    emp.getId(), auditDate, CorrectionStatus.APPROVED)) {
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
        return summaryRepository.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(employeeId, start, end)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public JobDailySummaryDTO getForDate(Long employeeId, LocalDate date) {
        return summaryRepository.findByEmployeeIdAndWorkDate(employeeId, date)
                .map(this::toDTO).orElse(null);
    }

    private JobDailySummaryDTO toDTO(JobDailySummary s) {
        return JobDailySummaryDTO.builder()
                .employeeId(s.getEmployee().getId())
                .employeeName(s.getEmployee().getFullName())
                .department(s.getEmployee().getDepartment())
                .workDate(s.getWorkDate())
                .totalWorkingMinutes(s.getTotalWorkingMinutes())
                .totalBreakMinutes(s.getTotalBreakMinutes())
                .sessionCount(s.getSessionCount())
                .firstLoginTime(s.getFirstLoginTime())
                .lastLogoutTime(s.getLastLogoutTime())
                .primaryClient(s.getPrimaryClient())
                .status(s.getStatus().name())
                .build();
    }
}
