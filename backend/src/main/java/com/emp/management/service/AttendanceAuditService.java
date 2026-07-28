package com.emp.management.service;

import com.emp.management.entity.EmployeeDetails;
import com.emp.management.entity.Role;
import com.emp.management.entity.User;
import com.emp.management.repository.AttendanceSessionRepository;
import com.emp.management.repository.EmployeeDetailsRepository;
import com.emp.management.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceAuditService {

    private final AttendanceSessionRepository sessionRepository;
    private final EmployeeDetailsRepository   employeeDetailsRepository;
    private final UserRepository              userRepository;
    private final EmailService                emailService;
    private final SystemSettingService        systemSettingService;

    private volatile LocalDate lastAuditSentDate = null;

    // Superseded by JobDailySummaryService.runUnderHoursAuditEmail() (11:00 AM) —
    // disabled (not @Scheduled) to avoid sending managers duplicate/conflicting
    // under-hours emails. Method kept for backward-compat / manual invocation;
    // the /api/settings/attendance-audit config panel is now inert.
    @Transactional
    public void runAttendanceAudit() {
        boolean enabled = systemSettingService.getBoolean("attendance_audit_enabled", false);
        if (!enabled) return;

        LocalTime now        = LocalTime.now();
        int configHour       = systemSettingService.getInt("attendance_audit_trigger_hour", 19);
        int configMinute     = systemSettingService.getInt("attendance_audit_trigger_minute", 0);
        if (now.getHour() != configHour || now.getMinute() != configMinute) return;

        LocalDate today = LocalDate.now();
        if (today.equals(lastAuditSentDate)) return;
        DayOfWeek dow = today.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) return;

        lastAuditSentDate = today;
        log.info("Attendance audit triggered for {}", today);

        int     minWorkMinutes   = systemSettingService.getInt("attendance_audit_min_work_hours", 8) * 60;
        boolean notifyEmployee   = systemSettingService.getBoolean("attendance_audit_notify_employee", false);
        boolean notifyManager    = systemSettingService.getBoolean("attendance_audit_notify_manager", true);
        boolean notifyHr         = systemSettingService.getBoolean("attendance_audit_notify_hr", false);
        boolean notifyAdmin      = systemSettingService.getBoolean("attendance_audit_notify_admin", true);

        List<String> adminEmails = userRepository.findByRoleIn(java.util.List.of(Role.ADMIN, Role.DIRECTOR)).stream()
                .map(User::getEmail).filter(e -> e != null && !e.isBlank())
                .collect(Collectors.toList());

        List<String> hrEmails = employeeDetailsRepository.findActiveHrEmployees().stream()
                .map(emp -> emp.getUser() != null ? emp.getUser().getEmail() : null)
                .filter(e -> e != null && !e.isBlank())
                .collect(Collectors.toList());

        int alertCount = 0;
        for (EmployeeDetails emp : employeeDetailsRepository.findByActive(true)) {
            if (emp.getUser() == null || emp.getUser().getRole() == Role.ADMIN || emp.getUser().getRole() == Role.DIRECTOR) continue;

            int workedMinutes = sessionRepository
                    .findByEmployeeIdAndWorkDateOrderByLoginTimeAsc(emp.getId(), today)
                    .stream()
                    .filter(s -> s.getLogoutTime() != null)
                    .mapToInt(s -> (int) ChronoUnit.MINUTES.between(s.getLoginTime(), s.getLogoutTime()))
                    .sum();

            if (workedMinutes == 0 || workedMinutes >= minWorkMinutes) continue;

            double workedHours   = workedMinutes / 60.0;
            double requiredHours = minWorkMinutes / 60.0;
            double deficitHours  = requiredHours - workedHours;
            String empEmail      = emp.getUser().getEmail();

            String primaryTo = null;
            Set<String> ccSet = new LinkedHashSet<>();

            if (notifyManager && emp.getManager() != null && emp.getManager().getUser() != null) {
                String mgrEmail = emp.getManager().getUser().getEmail();
                if (mgrEmail != null && !mgrEmail.isBlank()) primaryTo = mgrEmail;
            }

            if (notifyAdmin) {
                for (String ae : adminEmails) {
                    if (!ae.equals(primaryTo)) ccSet.add(ae);
                }
            }

            if (notifyHr) {
                for (String he : hrEmails) {
                    if (!he.equals(primaryTo)) ccSet.add(he);
                }
            }

            // If no primary recipient yet, promote first CC
            if (primaryTo == null && !ccSet.isEmpty()) {
                Iterator<String> it = ccSet.iterator();
                primaryTo = it.next();
                it.remove();
            }

            if (primaryTo == null) {
                log.warn("No recipients configured — skipping audit alert for {}", emp.getFullName());
                continue;
            }

            if (notifyEmployee && empEmail != null && !empEmail.isBlank() && !empEmail.equals(primaryTo)) {
                ccSet.add(empEmail);
            }

            try {
                emailService.sendAttendanceAuditAlert(
                        primaryTo, ccSet.isEmpty() ? null : ccSet.toArray(new String[0]),
                        emp.getFullName(), emp.getEmployeeCode(),
                        emp.getDepartment(), empEmail,
                        today, workedHours, requiredHours, deficitHours);
                alertCount++;
            } catch (Exception e) {
                log.error("Failed to send audit alert for {}: {}", emp.getFullName(), e.getMessage());
            }
        }
        log.info("Attendance audit complete: {} alert(s) sent for {}", alertCount, today);
    }
}
