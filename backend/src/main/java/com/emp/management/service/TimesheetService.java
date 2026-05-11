package com.emp.management.service;

import com.emp.management.dto.AttendanceSessionDTO;
import com.emp.management.dto.TimesheetDTO;
import com.emp.management.entity.*;
import com.emp.management.exception.BadRequestException;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
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
    private final EmailService emailService;

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
                if (finalTotal > 8.0 && !t.isAlertSent()) sendOvertimeAlerts(t, employee);
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

    // ── Overtime alerts ───────────────────────────────────────────────────────

    private void sendOvertimeAlerts(Timesheet timesheet, EmployeeDetails employee) {
        try {
            String recipientEmail = null;
            if (employee.getManager() != null && employee.getManager().getUser() != null) {
                recipientEmail = employee.getManager().getUser().getEmail();
            } else {
                // Fall back to any admin
                List<User> admins = userRepository.findByRole(Role.ADMIN);
                if (!admins.isEmpty()) recipientEmail = admins.get(0).getEmail();
            }
            if (recipientEmail != null) {
                emailService.sendOvertimeAlert(
                    recipientEmail,
                    employee.getFullName(),
                    timesheet.getWorkingHours(),
                    timesheet.getWorkDate()
                );
            }
            timesheet.setAlertSent(true);
            timesheetRepository.save(timesheet);
        } catch (Exception e) {
            log.error("Failed to send overtime alert", e);
        }
    }

    @Scheduled(cron = "0 0 * * * *")
    public void checkPendingOvertimeAlerts() {
        timesheetRepository.findByAlertSentFalseAndWorkingHoursGreaterThan(8.0)
                .forEach(t -> sendOvertimeAlerts(t, t.getEmployee()));
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
