package com.emp.management.service;

import com.emp.management.dto.AttendanceSessionDTO;
import com.emp.management.dto.TimesheetDTO;
import com.emp.management.entity.*;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
                if (!t.isManualOverride()) {
                    t.setWorkingHours(finalTotal);
                }
                timesheetRepository.save(t);
            });
        } catch (Exception e) {
            log.warn("Could not record logout for email={}: {}", email, e.getMessage());
        }
    }

    // ── Manual check-in / check-out (called from REST endpoints) ─────────────

    @Transactional
    public void checkIn(Long employeeId) {
        EmployeeDetails employee = findEmployee(employeeId);
        recordLogin(employee.getUser().getId());
    }

    @Transactional
    public void checkOut(Long employeeId) {
        EmployeeDetails employee = findEmployee(employeeId);
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

    // ── Admin: direct edit of working hours ───────────────────────────────────

    @Transactional
    public TimesheetDTO updateWorkingHours(Long empId, LocalDate date, Double hours) {
        EmployeeDetails emp = findEmployee(empId);
        Timesheet t = timesheetRepository.findByEmployeeIdAndWorkDate(empId, date)
                .orElse(Timesheet.builder().employee(emp).workDate(date).alertSent(false).build());
        t.setWorkingHours(hours);
        t.setManualOverride(true);
        return toDTO(timesheetRepository.save(t));
    }

    public java.util.Map<String, Double> getWorkingHoursMap(Long empId, int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end   = start.withDayOfMonth(start.lengthOfMonth());
        java.util.Map<String, Double> map = new java.util.HashMap<>();
        timesheetRepository.findByEmployeeIdAndDateRange(empId, start, end).forEach(t -> {
            if (t.getWorkingHours() != null) map.put(t.getWorkDate().toString(), t.getWorkingHours());
        });
        return map;
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
            managerEmail = userRepository.findByRole(Role.ADMIN).stream()
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
                userRepository.findByRole(Role.ADMIN).stream()
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
                .build();
    }

    // ── Daily missing-timesheet audit job (10:00 AM) ─────────────────────────

    @Scheduled(cron = "0 ${app.scheduler.daily-audit.minute:0} ${app.scheduler.daily-audit.hour:10} * * *", zone = "Asia/Kolkata")
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
            managerEmail = userRepository.findByRole(Role.ADMIN).stream()
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
                userRepository.findByRole(Role.ADMIN).stream()
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
                .build();
    }
}
