package com.emp.management.service;

import com.emp.management.dto.DailyWorkReportDTO;
import com.emp.management.entity.*;
import com.emp.management.entity.User;
import com.emp.management.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Arrays;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyWorkReportService {

    private final DailyWorkReportRepository reportRepository;
    private final AttendanceSessionRepository sessionRepository;
    private final EmployeeDetailsRepository employeeDetailsRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final SystemSettingService systemSettingService;

    @Value("${app.scheduler.daily-report.hour:23}")
    private int reportHour;

    @Value("${app.scheduler.daily-report.minute:0}")
    private int reportMinute;

    @Value("${app.work-report.email.enabled:true}")
    private boolean emailEnabled;

    // Tracks last date the work report email was sent — prevents duplicate sends within the same day
    private volatile LocalDate lastEmailSentDate = null;

    // ── Scheduled: generate reports for all employees at 11 PM ───────────────

    @Scheduled(cron = "0 ${app.scheduler.daily-report.minute:0} ${app.scheduler.daily-report.hour:23} * * *",
               zone = "Asia/Kolkata")
    @Transactional
    public void generateDailyReports() {
        LocalDate today = LocalDate.now();
        DayOfWeek dow = today.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            log.info("Daily work report skipped — {} is a weekend", today);
            return;
        }

        log.info("Generating daily work reports for {}", today);
        int count = 0;
        for (EmployeeDetails emp : employeeDetailsRepository.findByActive(true)) {
            try {
                if (emp.getUser() == null) continue;
                generateReportForEmployee(emp, today);
                count++;
            } catch (Exception e) {
                log.warn("Failed to generate report for {}: {}", emp.getFullName(), e.getMessage());
            }
        }
        log.info("Daily work reports generated/updated for {} employees on {}", count, today);
    }

    // ── Per-minute check: fire email at DB-configured time (no restart needed) ─

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Kolkata")
    @Transactional
    public void sendDailyWorkReportEmails() {
        boolean enabled = systemSettingService.getBoolean("work_report_email_enabled", emailEnabled);
        if (!enabled) return;

        LocalTime now = LocalTime.now();
        int configHour   = systemSettingService.getInt("work_report_email_hour",   10);
        int configMinute = systemSettingService.getInt("work_report_email_minute",  0);
        if (now.getHour() != configHour || now.getMinute() != configMinute) return;

        LocalDate today = LocalDate.now();
        if (today.equals(lastEmailSentDate)) return; // already sent today
        DayOfWeek dow = today.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) return;

        LocalDate reportDate = today.minusDays(1);
        log.info("Sending daily work report emails for {}", reportDate);
        lastEmailSentDate = today;

        generateAllReportsForDate(reportDate);

        boolean notifyEmployees = systemSettingService.getBoolean("work_report_notify_employees", true);
        boolean notifyAdmin     = systemSettingService.getBoolean("work_report_notify_admin",     true);

        List<DailyWorkReport> reports = reportRepository.findAllByDateRange(reportDate, reportDate);

        // Consolidated email to all Admins
        if (notifyAdmin) {
            List<DailyWorkReportDTO> consolidatedDtos = reportRepository.findByRolesAndDateRange(
                    Arrays.asList(Role.MANAGER, Role.ASSISTANT_MANAGER, Role.EMPLOYEE),
                    reportDate, reportDate)
                    .stream().map(this::toDTO).collect(Collectors.toList());
            int consolidatedSent = 0;
            for (User admin : userRepository.findByRole(Role.ADMIN)) {
                if (admin.getEmail() == null || admin.getEmail().isBlank()) continue;
                try {
                    emailService.sendConsolidatedWorkReportEmail(admin.getEmail(), reportDate, consolidatedDtos);
                    consolidatedSent++;
                } catch (Exception e) {
                    log.error("Failed to send consolidated report to {}: {}", admin.getEmail(), e.getMessage());
                }
            }
            log.info("Consolidated work report sent to {} admin(s)", consolidatedSent);
        }

        // Individual emails to each employee
        if (notifyEmployees && !reports.isEmpty()) {
            int individualSent = 0;
            for (DailyWorkReport r : reports) {
                try {
                    EmployeeDetails emp = r.getEmployee();
                    if (emp.getUser() == null || emp.getUser().getRole() == Role.ADMIN) continue;
                    if (emp.getUser().getEmail() == null) continue;
                    emailService.sendIndividualWorkReportEmail(
                            emp.getUser().getEmail(), emp.getFullName(),
                            r.getReportDate(), r.getFirstLoginTime(), r.getLastLogoutTime(),
                            r.getTotalOfficeMinutes(), r.getActiveMinutes(),
                            r.getBreakMinutes(), r.getSessionCount());
                    individualSent++;
                } catch (Exception e) {
                    log.error("Failed to send individual report to {}: {}", r.getEmployee().getFullName(), e.getMessage());
                }
            }
            log.info("Individual work reports sent to {} employee(s)", individualSent);
        }
    }

    // ── On-demand: generate reports for all active employees for a date ───────

    @Transactional
    public void generateAllReportsForDate(LocalDate date) {
        log.info("On-demand report generation triggered for {}", date);
        for (EmployeeDetails emp : employeeDetailsRepository.findByActive(true)) {
            try {
                if (emp.getUser() == null) continue;
                generateReportForEmployee(emp, date);
            } catch (Exception e) {
                log.warn("Failed to generate on-demand report for {}: {}", emp.getFullName(), e.getMessage());
            }
        }
    }

    @Transactional
    public void generateAllReportsForRange(LocalDate start, LocalDate end) {
        log.info("On-demand report generation triggered for range {} to {}", start, end);
        LocalDate current = start;
        while (!current.isAfter(end)) {
            final LocalDate date = current;
            DayOfWeek dow = date.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                for (EmployeeDetails emp : employeeDetailsRepository.findByActive(true)) {
                    try {
                        if (emp.getUser() == null) continue;
                        generateReportForEmployee(emp, date);
                    } catch (Exception e) {
                        log.warn("Failed to generate report for {} on {}: {}", emp.getFullName(), date, e.getMessage());
                    }
                }
            }
            current = current.plusDays(1);
        }
        log.info("Completed report generation for range {} to {}", start, end);
    }

    // ── Core: generate/update report for one employee on one date ────────────

    @Transactional
    public DailyWorkReport generateReportForEmployee(EmployeeDetails employee, LocalDate date) {
        List<AttendanceSession> completed = sessionRepository
                .findByEmployeeIdAndWorkDateOrderByLoginTimeAsc(employee.getId(), date)
                .stream()
                .filter(s -> s.getLogoutTime() != null)
                .collect(Collectors.toList());

        if (completed.isEmpty()) return null;

        LocalTime firstLogin  = completed.get(0).getLoginTime();
        LocalTime lastLogout  = completed.get(completed.size() - 1).getLogoutTime();

        int totalOfficeMinutes = (int) ChronoUnit.MINUTES.between(firstLogin, lastLogout);
        int activeMinutes = completed.stream()
                .mapToInt(s -> (int) ChronoUnit.MINUTES.between(s.getLoginTime(), s.getLogoutTime()))
                .sum();
        int breakMinutes = Math.max(0, totalOfficeMinutes - activeMinutes);

        Optional<DailyWorkReport> existing = reportRepository.findByEmployeeIdAndReportDate(employee.getId(), date);
        DailyWorkReport report = existing.orElse(DailyWorkReport.builder()
                .employee(employee).reportDate(date).build());

        report.setFirstLoginTime(firstLogin);
        report.setLastLogoutTime(lastLogout);
        report.setTotalOfficeMinutes(totalOfficeMinutes);
        report.setActiveMinutes(activeMinutes);
        report.setBreakMinutes(breakMinutes);
        report.setSessionCount(completed.size());

        return reportRepository.save(report);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DailyWorkReportDTO> getTeamReports(String email, LocalDate start, LocalDate end) {
        EmployeeDetails manager = employeeDetailsRepository.findByUserEmail(email).orElse(null);
        if (manager == null) return List.of();

        User user = manager.getUser();
        boolean isAdminOrHr = user != null &&
                (user.getRole() == Role.ADMIN || user.getRole() == Role.HR);

        List<DailyWorkReport> reports = isAdminOrHr
                ? reportRepository.findAllByDateRange(start, end)
                : reportRepository.findByManagerAndDateRange(manager.getId(), start, end);

        return reports.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DailyWorkReportDTO> getMyReports(String email, LocalDate start, LocalDate end) {
        EmployeeDetails emp = employeeDetailsRepository.findByUserEmail(email).orElse(null);
        if (emp == null) return List.of();
        return reportRepository.findByEmployeeIdAndReportDateBetweenOrderByReportDateDesc(emp.getId(), start, end)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    // ── CSV export ────────────────────────────────────────────────────────────

    public byte[] exportToCsv(String email, LocalDate start, LocalDate end) {
        List<DailyWorkReportDTO> reports = getTeamReports(email, start, end);
        StringBuilder sb = new StringBuilder();
        sb.append("Employee,Employee Code,Department,Date,First Login,Last Logout," +
                  "Total Office Hours,Active Working Hours,Break Duration,Sessions\n");
        for (DailyWorkReportDTO r : reports) {
            sb.append(String.join(",",
                    csvEscape(r.getEmployeeName()),
                    csvEscape(r.getEmployeeCode()),
                    csvEscape(r.getDepartment()),
                    r.getReportDate().toString(),
                    r.getFirstLoginTime() != null ? r.getFirstLoginTime().toString().substring(0, 5) : "",
                    r.getLastLogoutTime() != null ? r.getLastLogoutTime().toString().substring(0, 5) : "",
                    csvEscape(r.getTotalOfficeFormatted()),
                    csvEscape(r.getActiveFormatted()),
                    csvEscape(r.getBreakFormatted()),
                    String.valueOf(r.getSessionCount())
            )).append("\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String fmtMinutes(Integer minutes) {
        if (minutes == null || minutes < 0) return "—";
        int h = minutes / 60;
        int m = minutes % 60;
        return h + "h " + String.format("%02d", m) + "m";
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n"))
            return "\"" + value.replace("\"", "\"\"") + "\"";
        return value;
    }

    private DailyWorkReportDTO toDTO(DailyWorkReport r) {
        return DailyWorkReportDTO.builder()
                .id(r.getId())
                .employeeId(r.getEmployee().getId())
                .employeeName(r.getEmployee().getFullName())
                .employeeCode(r.getEmployee().getEmployeeCode())
                .department(r.getEmployee().getDepartment())
                .reportDate(r.getReportDate())
                .firstLoginTime(r.getFirstLoginTime())
                .lastLogoutTime(r.getLastLogoutTime())
                .totalOfficeMinutes(r.getTotalOfficeMinutes())
                .activeMinutes(r.getActiveMinutes())
                .breakMinutes(r.getBreakMinutes())
                .sessionCount(r.getSessionCount())
                .totalOfficeFormatted(fmtMinutes(r.getTotalOfficeMinutes()))
                .activeFormatted(fmtMinutes(r.getActiveMinutes()))
                .breakFormatted(fmtMinutes(r.getBreakMinutes()))
                .generatedAt(r.getGeneratedAt())
                .build();
    }
}
