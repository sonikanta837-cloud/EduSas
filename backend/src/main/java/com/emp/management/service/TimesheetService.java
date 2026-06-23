package com.emp.management.service;

import com.emp.management.dto.AttendanceSessionDTO;
import com.emp.management.dto.TimesheetDTO;
import com.emp.management.entity.*;
import com.emp.management.entity.AttendanceSessionStatus;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimesheetService {

    private final TimesheetRepository timesheetRepository;
    private final AttendanceSessionRepository sessionRepository;
    private final EmployeeDetailsRepository employeeDetailsRepository;
    private final UserRepository userRepository;
    private final LeaveRepository leaveRepository;
    private final EmailService emailService;
    private final TimesheetEntryRepository timesheetEntryRepository;
    private final SystemSettingService systemSettingService;

    @Value("${app.attendance.break-alert.enabled:true}")
    private boolean breakAlertEnabled;

    @Value("${app.attendance.break-alert.threshold-minutes:75}")
    private int breakAlertThresholdMinutes;

    @Value("${app.correction.enabled:true}")
    private boolean correctionEnabled;

    // ── Auto record on app login / logout ────────────────────────────────────

    @Transactional
    public void recordLogin(Long userId) {
        try {
            // Find employee for this user — skip if none (e.g. pure-admin accounts)
            EmployeeDetails employee = employeeDetailsRepository.findByUserId(userId).orElse(null);
            if (employee == null) return;

            LocalDate today = LocalDate.now();
            // If there's already an open session, don't create a duplicate
            if (sessionRepository.findTopByEmployeeIdAndWorkDateAndLogoutTimeIsNullOrderByLoginTimeDesc(
                    employee.getId(), today).isPresent()) return;

            AttendanceSession session = sessionRepository.save(AttendanceSession.builder()
                    .employee(employee)
                    .workDate(today)
                    .loginTime(LocalTime.now())
                    .build());

            // Ensure daily summary row exists (loginTime = first login ever today)
            timesheetRepository.findByEmployeeIdAndWorkDate(employee.getId(), today).ifPresentOrElse(
                    t -> { /* already exists */ },
                    () -> timesheetRepository.save(Timesheet.builder()
                            .employee(employee)
                            .workDate(today)
                            .loginTime(session.getLoginTime())
                            .alertSent(false)
                            .build())
            );
        } catch (Exception e) {
            log.warn("Could not record login for userId={}: {}", userId, e.getMessage());
        }
    }

    @Transactional
    public void recordLogout(String email) {
        try {
            EmployeeDetails employee = employeeDetailsRepository.findByUserEmail(email).orElse(null);
            if (employee == null) return;

            LocalDate today = LocalDate.now();
            AttendanceSession session = sessionRepository
                    .findTopByEmployeeIdAndWorkDateAndLogoutTimeIsNullOrderByLoginTimeDesc(employee.getId(), today)
                    .orElse(null);
            if (session == null) return; // no open session — nothing to close

            LocalTime logoutTime = LocalTime.now();
            double sessionHours = ChronoUnit.MINUTES.between(session.getLoginTime(), logoutTime) / 60.0;
            sessionHours = Math.round(sessionHours * 100.0) / 100.0;

            session.setLogoutTime(logoutTime);
            session.setSessionHours(sessionHours);
            sessionRepository.save(session);

            // Recalculate today's total hours
            double totalHours = sessionRepository
                    .findByEmployeeIdAndWorkDateOrderByLoginTimeAsc(employee.getId(), today)
                    .stream()
                    .filter(s -> s.getSessionHours() != null)
                    .mapToDouble(AttendanceSession::getSessionHours)
                    .sum();
            totalHours = Math.round(totalHours * 100.0) / 100.0;

            final double finalTotal = totalHours;
            timesheetRepository.findByEmployeeIdAndWorkDate(employee.getId(), today).ifPresent(t -> {
                t.setLogoutTime(logoutTime);
                t.setWorkingHours(finalTotal);
                timesheetRepository.save(t);
            });

            checkAndSendBreakAlert(employee, today, "LOGOUT");
        } catch (Exception e) {
            log.warn("Could not record logout for email={}: {}", email, e.getMessage());
        }
    }

    // ── Manual check-in / check-out (called from REST endpoints) ─────────────

    @Transactional
    public void checkIn(Long employeeId) {
        EmployeeDetails employee = findEmployee(employeeId);
        if (employee.getUser() == null) {
            throw new com.emp.management.exception.BadRequestException(
                    "Employee has no associated user account");
        }
        recordLogin(employee.getUser().getId());
    }

    @Transactional
    public void checkOut(Long employeeId) {
        EmployeeDetails employee = findEmployee(employeeId);
        if (employee.getUser() == null) {
            throw new com.emp.management.exception.BadRequestException(
                    "Employee has no associated user account");
        }
        recordLogout(employee.getUser().getEmail());
    }

    // ── Session queries ───────────────────────────────────────────────────────

    public List<AttendanceSessionDTO> getTodaySessions(Long employeeId) {
        return sessionRepository
                .findByEmployeeIdAndWorkDateOrderByLoginTimeAsc(employeeId, LocalDate.now())
                .stream().map(this::toSessionDTO).collect(Collectors.toList());
    }

    public List<AttendanceSessionDTO> getSessionsByRange(Long employeeId, LocalDate start, LocalDate end) {
        return sessionRepository
                .findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAscLoginTimeAsc(employeeId, start, end)
                .stream().map(this::toSessionDTO).collect(Collectors.toList());
    }

    // ── Legacy Timesheet queries (kept for backward-compat) ───────────────────

    public List<TimesheetDTO> getMyTimesheets(Long employeeId) {
        return timesheetRepository.findByEmployeeIdOrderByWorkDateDesc(employeeId).stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    public List<TimesheetDTO> getTimesheetsByDateRange(Long employeeId, LocalDate start, LocalDate end) {
        return timesheetRepository.findByEmployeeIdAndDateRange(employeeId, start, end).stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    public List<TimesheetDTO> getTimesheetsByDate(LocalDate date) {
        return timesheetRepository.findByWorkDate(date).stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    public TimesheetDTO getTodayTimesheet(Long employeeId) {
        return timesheetRepository.findByEmployeeIdAndWorkDate(employeeId, LocalDate.now())
                .map(this::toDTO).orElse(null);
    }


    // ── Daily attendance audit job (10:00 AM) ────────────────────────────────

    @Scheduled(cron = "0 ${app.scheduler.daily-audit.minute:0} ${app.scheduler.daily-audit.hour:10} * * *", zone = "Asia/Kolkata")
    @Transactional
    public void runDailyAttendanceAudit() {
        LocalDate auditDate = LocalDate.now().minusDays(1);

        DayOfWeek dow = auditDate.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            log.info("Attendance audit skipped — {} is a weekend", auditDate);
            return;
        }

        log.info("Running daily attendance audit for {}", auditDate);
        int alerted = 0;

        for (EmployeeDetails emp : employeeDetailsRepository.findByActive(true)) {
            try {
                if (emp.getUser() == null) continue;

                // Exclude: approved leave of any type
                // (covers regular leave, half-day leave, and public holidays —
                //  all stored as Leave records with status = APPROVED)
                if (leaveRepository.hasLeaveOnDate(emp.getId(), auditDate, LeaveStatus.APPROVED)) continue;

                Timesheet ts = timesheetRepository
                        .findByEmployeeIdAndWorkDate(emp.getId(), auditDate).orElse(null);

                // Exclude: regularised / manually overridden attendance
                if (ts != null && ts.isManualOverride()) continue;

                // Exclude: already notified for this date
                if (ts != null && ts.isAlertSent()) continue;

                double worked   = (ts != null && ts.getWorkingHours() != null) ? ts.getWorkingHours() : 0.0;
                double required = 8.0;
                if (worked >= required) continue;

                double deficit = Math.round((required - worked) * 100.0) / 100.0;

                dispatchAttendanceAuditAlert(emp, auditDate, worked, required, deficit);
                alerted++;

                // Mark notified — ensures this job never fires twice for the same date
                if (ts == null) {
                    ts = Timesheet.builder()
                            .employee(emp).workDate(auditDate)
                            .alertSent(true).build();
                } else {
                    ts.setAlertSent(true);
                }
                timesheetRepository.save(ts);

            } catch (Exception e) {
                log.warn("Attendance audit failed for employee {}: {}", emp.getFullName(), e.getMessage());
            }
        }

        log.info("Daily attendance audit completed for {} — {} alert(s) sent", auditDate, alerted);
    }

    private void dispatchAttendanceAuditAlert(EmployeeDetails emp, LocalDate date,
                                              double worked, double required, double deficit) {
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
            log.warn("No manager or admin found for attendance audit alert — employee: {}", emp.getFullName());
            return;
        }

        // CC: all HR + Admin (deduplicated, excluding the primary To recipient)
        final String primaryTo = managerEmail;
        String[] cc = Stream.concat(
                userRepository.findByRole(Role.HR).stream(),
                userRepository.findByRoleIn(java.util.List.of(Role.ADMIN, Role.DIRECTOR)).stream()
        )
        .map(User::getEmail)
        .filter(e -> e != null && !e.isBlank() && !e.equalsIgnoreCase(primaryTo))
        .distinct()
        .toArray(String[]::new);

        emailService.sendAttendanceAuditAlert(
                managerEmail, cc,
                emp.getFullName(),
                emp.getEmployeeCode(),
                emp.getDepartment(),
                emp.getUser().getEmail(),
                date, worked, required, deficit
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private EmployeeDetails findEmployee(Long id) {
        return employeeDetailsRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", id));
    }

    private AttendanceSessionDTO toSessionDTO(AttendanceSession s) {
        return AttendanceSessionDTO.builder()
                .id(s.getId())
                .employeeId(s.getEmployee().getId())
                .workDate(s.getWorkDate())
                .loginTime(s.getLoginTime())
                .logoutTime(s.getLogoutTime())
                .sessionHours(s.getSessionHours())
                .status(s.getStatus() != null ? s.getStatus().name() : "COMPLETE")
                .build();
    }

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
        String[] cc = Stream.concat(
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

    private TimesheetDTO toDTO(Timesheet t) {
        return TimesheetDTO.builder()
                .id(t.getId())
                .employeeId(t.getEmployee().getId())
                .employeeName(t.getEmployee().getFullName())
                .department(t.getEmployee().getDepartment())
                .workDate(t.getWorkDate())
                .loginTime(t.getLoginTime())
                .logoutTime(t.getLogoutTime())
                .workingHours(t.getWorkingHours())
                .notes(t.getNotes())
                .alertSent(t.isAlertSent())
                .missingAlertSent(t.isMissingAlertSent())
                .breakAlertSent(t.isBreakAlertSent())
                .build();
    }

    // ── Break time alert (on logout + end-of-day scheduled job) ─────────────

    private void checkAndSendBreakAlert(EmployeeDetails employee, LocalDate date) {
        checkAndSendBreakAlert(employee, date, "BOTH");
    }

    private void checkAndSendBreakAlert(EmployeeDetails employee, LocalDate date, String triggerSource) {
        boolean enabled = systemSettingService.getBoolean("break_alert_enabled", breakAlertEnabled);
        if (!enabled) return;
        if (employee.getUser() != null && (Role.ADMIN == employee.getUser().getRole() || Role.DIRECTOR == employee.getUser().getRole())) return;

        String frequency = systemSettingService.getString("break_alert_frequency", "BOTH");
        if ("ON_LOGOUT".equals(frequency) && "SCHEDULED".equals(triggerSource)) return;
        if ("SCHEDULED".equals(frequency) && "LOGOUT".equals(triggerSource)) return;

        List<AttendanceSession> completed = sessionRepository
                .findByEmployeeIdAndWorkDateOrderByLoginTimeAsc(employee.getId(), date)
                .stream()
                .filter(s -> s.getLogoutTime() != null)
                .collect(Collectors.toList());

        // Need at least two sessions to accumulate a gap (break) between them
        if (completed.size() < 2) return;

        LocalTime firstLogin  = completed.get(0).getLoginTime();
        LocalTime lastLogout  = completed.get(completed.size() - 1).getLogoutTime();

        long daySpanMinutes   = ChronoUnit.MINUTES.between(firstLogin, lastLogout);
        long activeMinutes    = completed.stream()
                .mapToLong(s -> ChronoUnit.MINUTES.between(s.getLoginTime(), s.getLogoutTime()))
                .sum();
        long breakMinutes     = daySpanMinutes - activeMinutes;

        int threshold = systemSettingService.getInt("break_alert_threshold_minutes", breakAlertThresholdMinutes);
        if (breakMinutes <= threshold) return;

        Timesheet ts = timesheetRepository.findByEmployeeIdAndWorkDate(employee.getId(), date).orElse(null);
        if (ts != null && ts.isBreakAlertSent()) return;

        dispatchBreakTimeAlert(employee, date, firstLogin, lastLogout, breakMinutes, threshold);

        if (ts == null) {
            ts = Timesheet.builder()
                    .employee(employee).workDate(date)
                    .breakAlertSent(true).alertSent(false).build();
        } else {
            ts.setBreakAlertSent(true);
        }
        timesheetRepository.save(ts);
    }

    private void dispatchBreakTimeAlert(EmployeeDetails emp, LocalDate date,
                                         LocalTime firstLogin, LocalTime lastLogout,
                                         long breakMinutes, int threshold) {
        boolean notifyManager = systemSettingService.getBoolean("break_alert_notify_manager", true);
        boolean notifyHr      = systemSettingService.getBoolean("break_alert_notify_hr", true);
        boolean notifyAdmin   = systemSettingService.getBoolean("break_alert_notify_admin", false);

        String primaryTo = null;

        if (notifyManager) {
            primaryTo = (emp.getManager() != null && emp.getManager().getUser() != null)
                    ? emp.getManager().getUser().getEmail() : null;
        }
        if (primaryTo == null && notifyAdmin) {
            primaryTo = userRepository.findByRoleIn(java.util.List.of(Role.ADMIN, Role.DIRECTOR)).stream()
                    .map(User::getEmail)
                    .filter(e -> e != null && !e.isBlank())
                    .findFirst().orElse(null);
        }
        if (primaryTo == null) {
            log.warn("No recipient configured for break time alert — employee: {}", emp.getFullName());
            return;
        }

        final String to = primaryTo;
        java.util.List<String> ccList = new java.util.ArrayList<>();
        if (notifyHr) {
            userRepository.findByRole(Role.HR).stream()
                    .map(User::getEmail)
                    .filter(e -> e != null && !e.isBlank() && !e.equalsIgnoreCase(to))
                    .forEach(ccList::add);
        }
        if (notifyAdmin) {
            userRepository.findByRoleIn(java.util.List.of(Role.ADMIN, Role.DIRECTOR)).stream()
                    .map(User::getEmail)
                    .filter(e -> e != null && !e.isBlank() && !e.equalsIgnoreCase(to))
                    .forEach(ccList::add);
        }
        String[] cc = ccList.stream().distinct().toArray(String[]::new);

        emailService.sendBreakTimeAlertEmail(
                to, cc,
                emp.getFullName(),
                emp.getEmployeeCode(),
                emp.getDepartment(),
                emp.getUser().getEmail(),
                date, firstLogin, lastLogout, breakMinutes, threshold
        );
    }

    // ── Midnight: mark sessions with missing logout as PENDING_LOGOUT_CONFIRMATION ─

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void runMissingLogoutDetection() {
        if (!correctionEnabled) return;

        // Find past sessions (before today) with no logout that haven't been flagged yet
        List<AttendanceSession> openSessions = sessionRepository
                .findByWorkDateBeforeAndLogoutTimeIsNullAndStatus(
                        LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")), AttendanceSessionStatus.COMPLETE);

        int marked = 0;
        for (AttendanceSession session : openSessions) {
            DayOfWeek dow = session.getWorkDate().getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) continue;
            session.setStatus(AttendanceSessionStatus.PENDING_LOGOUT_CONFIRMATION);
            sessionRepository.save(session);
            marked++;
        }
        log.info("Missing-logout detection: {} session(s) marked PENDING_LOGOUT_CONFIRMATION", marked);
    }

    @Scheduled(cron = "0 ${app.scheduler.break-audit.minute:30} ${app.scheduler.break-audit.hour:19} * * *", zone = "Asia/Kolkata")
    @Transactional
    public void runBreakTimeAudit() {
        LocalDate today = LocalDate.now();
        DayOfWeek dow = today.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            log.info("Break time audit skipped — {} is a weekend", today);
            return;
        }

        boolean enabled = systemSettingService.getBoolean("break_alert_enabled", breakAlertEnabled);
        if (!enabled) {
            log.info("Break time alert is disabled — skipping audit for {}", today);
            return;
        }

        log.info("Running break time audit for {}", today);
        int checked = 0;

        for (EmployeeDetails emp : employeeDetailsRepository.findByActive(true)) {
            try {
                if (emp.getUser() == null) continue;

                Timesheet ts = timesheetRepository.findByEmployeeIdAndWorkDate(emp.getId(), today).orElse(null);
                if (ts != null && ts.isBreakAlertSent()) continue;

                if (leaveRepository.hasLeaveOnDate(emp.getId(), today, LeaveStatus.APPROVED)) continue;

                checkAndSendBreakAlert(emp, today, "SCHEDULED");
                checked++;
            } catch (Exception e) {
                log.warn("Break time audit failed for employee {}: {}", emp.getFullName(), e.getMessage());
            }
        }

        log.info("Break time audit completed for {} — {} employee(s) evaluated", today, checked);
    }
}
