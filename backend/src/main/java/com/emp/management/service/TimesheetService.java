package com.emp.management.service;

import com.emp.management.entity.*;
import com.emp.management.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Handles the "did you log your project hours" nudge (TimesheetEntry) —
 * unrelated to attendance, which is now sourced entirely from Job Time
 * Tracking (see JobDailySummaryService).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TimesheetService {

    private final TimesheetRepository timesheetRepository;
    private final EmployeeDetailsRepository employeeDetailsRepository;
    private final UserRepository userRepository;
    private final LeaveRepository leaveRepository;
    private final EmailService emailService;
    private final TimesheetEntryRepository timesheetEntryRepository;

    // ── Daily missing-timesheet audit job (10:30 AM by default) ─────────────

    @Scheduled(cron = "0 ${app.scheduler.missing-ts-audit.minute:30} ${app.scheduler.missing-ts-audit.hour:10} * * *", zone = "Asia/Kolkata")
    @Transactional
    public void runMissingTimesheetAudit() {
        // Resolve previous working day (skips weekends — handles Monday → Friday)
        LocalDate auditDate = LocalDate.now().minusDays(1);
        while (auditDate.getDayOfWeek() == DayOfWeek.SATURDAY
                || auditDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            auditDate = auditDate.minusDays(1);
        }

        log.info("Running missing-timesheet audit for {}", auditDate);
        int alerted = 0;

        for (EmployeeDetails emp : employeeDetailsRepository.findByActive(true)) {
            try {
                if (emp.getUser() == null) continue;

                // Exclude: approved leave of any type (regular, half-day, public holiday)
                if (leaveRepository.hasLeaveOnDate(emp.getId(), auditDate, LeaveStatus.APPROVED)) continue;

                Timesheet ts = timesheetRepository
                        .findByEmployeeIdAndWorkDate(emp.getId(), auditDate).orElse(null);

                // Exclude: regularised / manually overridden attendance
                if (ts != null && ts.isManualOverride()) continue;

                // Exclude: already notified for this date — prevents duplicates on restart
                if (ts != null && ts.isMissingAlertSent()) continue;

                // Exclude: timesheet entries already submitted
                if (timesheetEntryRepository.existsByEmployeeIdAndDate(emp.getId(), auditDate)) continue;

                dispatchMissingTimesheetAlert(emp, auditDate);
                alerted++;

                // Persist flag so this date is never re-notified
                if (ts == null) {
                    ts = Timesheet.builder()
                            .employee(emp).workDate(auditDate)
                            .missingAlertSent(true).alertSent(false).build();
                } else {
                    ts.setMissingAlertSent(true);
                }
                timesheetRepository.save(ts);

            } catch (Exception e) {
                log.warn("Missing-timesheet audit failed for employee {}: {}", emp.getFullName(), e.getMessage());
            }
        }

        log.info("Missing-timesheet audit completed for {} — {} alert(s) sent", auditDate, alerted);
    }

    private void dispatchMissingTimesheetAlert(EmployeeDetails emp, LocalDate date) {
        // Primary recipient: reporting manager; fall back to first active admin
        String managerEmail = (emp.getManager() != null && emp.getManager().getUser() != null)
                ? emp.getManager().getUser().getEmail() : null;
        if (managerEmail == null) {
            managerEmail = userRepository.findByRoleIn(java.util.List.of(Role.ADMIN, Role.DIRECTOR)).stream()
                    .map(User::getEmail)
                    .filter(e -> e != null && !e.isBlank())
                    .findFirst().orElse(null);
        }
        if (managerEmail == null) {
            log.warn("No manager or admin found for missing-timesheet alert — employee: {}", emp.getFullName());
            return;
        }

        // CC: all HR + Admin (deduplicated, excluding the primary To recipient)
        final String primaryTo = managerEmail;
        String[] cc = java.util.stream.Stream.concat(
                userRepository.findByRole(Role.HR).stream(),
                userRepository.findByRoleIn(java.util.List.of(Role.ADMIN, Role.DIRECTOR)).stream()
        )
        .map(User::getEmail)
        .filter(e -> e != null && !e.isBlank() && !e.equalsIgnoreCase(primaryTo))
        .distinct()
        .toArray(String[]::new);

        emailService.sendMissingTimesheetManagerAlert(
                managerEmail, cc,
                emp.getFullName(),
                emp.getEmployeeCode(),
                emp.getDepartment(),
                emp.getUser().getEmail(),
                date
        );
    }
}
