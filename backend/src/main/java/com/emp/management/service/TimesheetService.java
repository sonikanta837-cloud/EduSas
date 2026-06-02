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

    // ── Daily under-hours alert (10:00 AM) ───────────────────────────────────

    @Scheduled(cron = "0 0 10 * * *")
    public void sendDailyUnderhoursAlerts() {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        // Skip weekends — no working-hours expectation on Sat/Sun
        DayOfWeek dow = yesterday.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) return;

        List<String> adminEmails = userRepository.findByRole(Role.ADMIN).stream()
                .map(u -> u.getEmail())
                .filter(e -> e != null && !e.isBlank())
                .collect(Collectors.toList());

        for (EmployeeDetails emp : employeeDetailsRepository.findByActive(true)) {
            try {
                if (emp.getUser() == null) continue;

                // Skip employees on approved leave yesterday
                if (leaveRepository.hasLeaveOnDate(emp.getId(), yesterday, LeaveStatus.APPROVED)) continue;

                Timesheet ts = timesheetRepository
                        .findByEmployeeIdAndWorkDate(emp.getId(), yesterday).orElse(null);

                // Already alerted for this day
                if (ts != null && ts.isAlertSent()) continue;

                double hours = (ts != null && ts.getWorkingHours() != null) ? ts.getWorkingHours() : 0.0;
                if (hours >= 8.0) continue;

                dispatchUnderhoursAlert(emp, yesterday, hours, adminEmails);

                // Persist alert flag so we never send twice for the same day
                if (ts == null) {
                    ts = Timesheet.builder()
                            .employee(emp).workDate(yesterday)
                            .alertSent(true).build();
                } else {
                    ts.setAlertSent(true);
                }
                timesheetRepository.save(ts);

            } catch (Exception e) {
                log.warn("Underhours alert failed for {}: {}", emp.getFullName(), e.getMessage());
            }
        }
    }

    private void dispatchUnderhoursAlert(EmployeeDetails emp, LocalDate date,
                                         double hours, List<String> adminEmails) {
        String empEmail = emp.getUser().getEmail();
        Role empRole   = emp.getUser().getRole();
        String managerEmail = (emp.getManager() != null && emp.getManager().getUser() != null)
                ? emp.getManager().getUser().getEmail() : null;

        List<String> toList = new ArrayList<>();

        switch (empRole) {
            case MANAGER:
                // Only their manager; no manager → admin
                if (managerEmail != null) {
                    toList.add(managerEmail);
                } else {
                    adminEmails.stream().filter(e -> !e.equalsIgnoreCase(empEmail)).forEach(toList::add);
                }
                break;
            default:
                // ASSISTANT_MANAGER, EMPLOYEE, HR, ADMIN → manager + all admins
                if (managerEmail != null) toList.add(managerEmail);
                adminEmails.stream().filter(e -> !e.equalsIgnoreCase(empEmail)).forEach(toList::add);
                break;
        }

        // Deduplicate
        toList = toList.stream().distinct().filter(e -> !e.isBlank()).collect(Collectors.toList());
        if (toList.isEmpty()) return;

        emailService.sendUnderhoursAlert(
                toList.toArray(new String[0]),
                new String[]{ empEmail },
                emp.getFullName(), date, hours);
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

    // ── Daily missing-timesheet reminder (10:00 AM next morning) ─────────────

    @Scheduled(cron = "0 0 10 * * MON-FRI")
    public void sendTimesheetMissingReminders() {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        // Skip if yesterday was a weekend (e.g. this runs Mon → checks Sun)
        DayOfWeek dow = yesterday.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) return;

        log.info("Running missing-timesheet reminder check for {}", yesterday);

        for (EmployeeDetails emp : employeeDetailsRepository.findByActive(true)) {
            try {
                if (emp.getUser() == null) continue;

                // Admins are excluded — they may not track project hours
                Role role = emp.getUser().getRole();
                if (role == Role.ADMIN) continue;

                // Skip employees who were on approved leave yesterday
                if (leaveRepository.hasLeaveOnDate(emp.getId(), yesterday, LeaveStatus.APPROVED)) continue;

                // Skip employees who already have at least one project entry for yesterday
                if (timesheetEntryRepository.existsByEmployeeIdAndDate(emp.getId(), yesterday)) continue;

                String empEmail    = emp.getUser().getEmail();
                String managerEmail = (emp.getManager() != null && emp.getManager().getUser() != null)
                        ? emp.getManager().getUser().getEmail() : null;

                emailService.sendTimesheetMissingReminder(empEmail, emp.getFullName(), managerEmail, yesterday);

            } catch (Exception e) {
                log.warn("Timesheet reminder failed for {}: {}", emp.getFullName(), e.getMessage());
            }
        }
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
                .build();
    }
}
