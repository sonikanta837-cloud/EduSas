package com.emp.management.service;

import com.emp.management.dto.AttendanceSessionDTO;
import com.emp.management.dto.AuditLogDTO;
import com.emp.management.dto.CorrectionRequestDTO;
import com.emp.management.entity.*;
import com.emp.management.exception.BadRequestException;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CorrectionRequestService {

    private final CorrectionRequestRepository correctionRequestRepository;
    private final TimesheetAuditLogRepository auditLogRepository;
    private final AttendanceSessionRepository sessionRepository;
    private final TimesheetRepository timesheetRepository;
    private final EmployeeDetailsRepository employeeDetailsRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${app.correction.enabled:true}")
    private boolean correctionEnabled;

    @Value("${app.correction.correction-window-days:3}")
    private int correctionWindowDays;

    @Value("${app.correction.email-notifications-enabled:true}")
    private boolean emailNotificationsEnabled;

    @Value("${app.correction.hr-notifications-enabled:true}")
    private boolean hrNotificationsEnabled;

    @Value("${app.file.upload-dir:./uploads}")
    private String uploadDir;

    // ── Pending sessions for the popup ───────────────────────────────────────

    @Transactional
    public List<AttendanceSessionDTO> getPendingSessions(String email) {
        EmployeeDetails employee = employeeDetailsRepository.findByUserEmail(email).orElse(null);
        if (employee == null) return List.of();

        // Run detection inline so it works even when the scheduled cron didn't fire
        // (e.g. Railway sleeping at midnight). Marks any past sessions missing a logout.
        if (correctionEnabled) {
            LocalDate todayIst = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
            sessionRepository
                    .findByWorkDateBeforeAndLogoutTimeIsNullAndStatus(todayIst, AttendanceSessionStatus.COMPLETE)
                    .forEach(s -> {
                        java.time.DayOfWeek dow = s.getWorkDate().getDayOfWeek();
                        if (dow != java.time.DayOfWeek.SATURDAY && dow != java.time.DayOfWeek.SUNDAY) {
                            s.setStatus(AttendanceSessionStatus.PENDING_LOGOUT_CONFIRMATION);
                            sessionRepository.save(s);
                        }
                    });
        }

        return sessionRepository
                .findByEmployeeIdAndStatusOrderByWorkDateDesc(
                        employee.getId(), AttendanceSessionStatus.PENDING_LOGOUT_CONFIRMATION)
                .stream()
                .filter(s -> ChronoUnit.DAYS.between(s.getWorkDate(), LocalDate.now()) <= correctionWindowDays)
                .map(this::toSessionDTO)
                .collect(Collectors.toList());
    }

    // ── Submit correction request ─────────────────────────────────────────────

    @Transactional
    public CorrectionRequestDTO submit(String email, Long sessionId,
                                        String requestedLogoutTimeStr, String reason,
                                        MultipartFile attachment) {
        if (!correctionEnabled) throw new BadRequestException("Correction requests are currently disabled");

        EmployeeDetails employee = employeeDetailsRepository.findByUserEmail(email)
                .orElseThrow(() -> new BadRequestException("Employee profile not found"));

        AttendanceSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session", sessionId));

        if (!session.getEmployee().getId().equals(employee.getId()))
            throw new BadRequestException("Session does not belong to this employee");

        if (session.getStatus() != AttendanceSessionStatus.PENDING_LOGOUT_CONFIRMATION)
            throw new BadRequestException("This session is not pending logout confirmation");

        long daysSince = ChronoUnit.DAYS.between(session.getWorkDate(), LocalDate.now());
        if (daysSince > correctionWindowDays)
            throw new BadRequestException("Correction window has expired (" + correctionWindowDays + " days)");

        if (correctionRequestRepository.existsBySessionIdAndStatus(sessionId, CorrectionStatus.PENDING_MANAGER_APPROVAL))
            throw new BadRequestException("A pending correction request already exists for this session");

        LocalTime requestedLogoutTime = LocalTime.parse(requestedLogoutTimeStr);

        // Allow next-day logout (e.g. login 21:35, logout 05:35 next morning)
        boolean sameOrNextDay = requestedLogoutTime.isAfter(session.getLoginTime())
                || session.getLoginTime().isAfter(LocalTime.of(18, 0)); // late login implies next-day logout
        if (!sameOrNextDay)
            throw new BadRequestException("Requested logout time must be after login time (" + session.getLoginTime() + ")");

        String attachmentPath = null;
        if (attachment != null && !attachment.isEmpty()) {
            attachmentPath = saveAttachment(attachment, employee.getId(), session.getWorkDate());
        }

        TimesheetCorrectionRequest request = correctionRequestRepository.save(
                TimesheetCorrectionRequest.builder()
                        .employee(employee)
                        .session(session)
                        .workDate(session.getWorkDate())
                        .loginTime(session.getLoginTime())
                        .requestedLogoutTime(requestedLogoutTime)
                        .reason(reason)
                        .attachmentPath(attachmentPath)
                        .status(CorrectionStatus.PENDING_MANAGER_APPROVAL)
                        .build()
        );

        saveAuditLog(employee, request, "SUBMITTED", null, null, null);

        if (emailNotificationsEnabled) notifySubmission(employee, request);

        return toDTO(request);
    }

    // ── Approve ───────────────────────────────────────────────────────────────

    @Transactional
    public CorrectionRequestDTO approve(Long requestId, String approverEmail,
                                         String approvedLogoutTimeStr, String comment) {
        TimesheetCorrectionRequest request = findPending(requestId);

        User approver = userRepository.findByEmail(approverEmail)
                .orElseThrow(() -> new BadRequestException("Approver not found"));

        LocalTime approvedLogout = LocalTime.parse(approvedLogoutTimeStr);

        boolean validLogout = approvedLogout.isAfter(request.getLoginTime())
                || request.getLoginTime().isAfter(LocalTime.of(18, 0));
        if (!validLogout)
            throw new BadRequestException("Approved logout time must be after login time (" + request.getLoginTime() + ")");

        // Update session
        AttendanceSession session = request.getSession();
        double sessionHours = Math.round(
                ChronoUnit.MINUTES.between(session.getLoginTime(), approvedLogout) / 60.0 * 100.0) / 100.0;
        session.setLogoutTime(approvedLogout);
        session.setSessionHours(sessionHours);
        session.setStatus(AttendanceSessionStatus.COMPLETE);
        sessionRepository.save(session);

        // Recalculate timesheet
        EmployeeDetails employee = request.getEmployee();
        double totalHours = Math.round(
                sessionRepository.findByEmployeeIdAndWorkDateOrderByLoginTimeAsc(employee.getId(), request.getWorkDate())
                        .stream()
                        .filter(s -> s.getSessionHours() != null)
                        .mapToDouble(AttendanceSession::getSessionHours)
                        .sum() * 100.0) / 100.0;
        final double finalTotal = totalHours;

        timesheetRepository.findByEmployeeIdAndWorkDate(employee.getId(), request.getWorkDate())
                .ifPresentOrElse(ts -> {
                    ts.setWorkingHours(finalTotal);
                    ts.setLogoutTime(approvedLogout);
                    timesheetRepository.save(ts);
                }, () -> timesheetRepository.save(Timesheet.builder()
                        .employee(employee).workDate(session.getWorkDate())
                        .loginTime(session.getLoginTime()).logoutTime(approvedLogout)
                        .workingHours(finalTotal).build()));

        // Resolve request
        String approverName = employeeDetailsRepository.findByUserId(approver.getId())
                .map(EmployeeDetails::getFullName).orElse(approver.getEmail());

        request.setStatus(CorrectionStatus.APPROVED);
        request.setResolvedBy(approver);
        request.setResolvedAt(LocalDateTime.now());
        request.setResolverComment(comment);
        request = correctionRequestRepository.save(request);

        saveAuditLog(employee, request, "APPROVED", approverName, comment, approvedLogout);

        if (emailNotificationsEnabled) notifyApproval(employee, request, approverName, approvedLogout);

        return toDTO(request);
    }

    // ── Reject ────────────────────────────────────────────────────────────────

    @Transactional
    public CorrectionRequestDTO reject(Long requestId, String rejectorEmail, String comment) {
        TimesheetCorrectionRequest request = findPending(requestId);

        User rejector = userRepository.findByEmail(rejectorEmail)
                .orElseThrow(() -> new BadRequestException("Rejector not found"));

        String rejectorName = employeeDetailsRepository.findByUserId(rejector.getId())
                .map(EmployeeDetails::getFullName).orElse(rejector.getEmail());

        request.setStatus(CorrectionStatus.REJECTED);
        request.setResolvedBy(rejector);
        request.setResolvedAt(LocalDateTime.now());
        request.setResolverComment(comment);
        request = correctionRequestRepository.save(request);

        saveAuditLog(request.getEmployee(), request, "REJECTED", rejectorName, comment, null);

        if (emailNotificationsEnabled) notifyRejection(request.getEmployee(), request, rejectorName);

        return toDTO(request);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public List<CorrectionRequestDTO> getMyRequests(String email) {
        EmployeeDetails emp = employeeDetailsRepository.findByUserEmail(email).orElse(null);
        if (emp == null) return List.of();
        return correctionRequestRepository.findByEmployeeIdOrderByCreatedAtDesc(emp.getId())
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<CorrectionRequestDTO> getAllPending() {
        return correctionRequestRepository.findByStatusOrderByCreatedAtDesc(CorrectionStatus.PENDING_MANAGER_APPROVAL)
                .stream()
                .filter(r -> r.getEmployee().getUser() == null
                        || (r.getEmployee().getUser().getRole() != Role.ADMIN
                            && r.getEmployee().getUser().getRole() != Role.DIRECTOR))
                .map(this::toDTO).collect(Collectors.toList());
    }

    public List<CorrectionRequestDTO> getPendingForTeam(String email) {
        EmployeeDetails manager = employeeDetailsRepository.findByUserEmail(email).orElse(null);
        if (manager == null) return List.of();
        return correctionRequestRepository
                .findByEmployee_Manager_IdAndStatusOrderByCreatedAtDesc(
                        manager.getId(), CorrectionStatus.PENDING_MANAGER_APPROVAL)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<AuditLogDTO> getAuditLogs() {
        return auditLogRepository.findAllByOrderByActionDateTimeDesc()
                .stream().map(this::toAuditDTO).collect(Collectors.toList());
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private TimesheetCorrectionRequest findPending(Long requestId) {
        TimesheetCorrectionRequest r = correctionRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("CorrectionRequest", requestId));
        if (r.getStatus() != CorrectionStatus.PENDING_MANAGER_APPROVAL)
            throw new BadRequestException("Request is no longer in pending state");
        return r;
    }

    private void saveAuditLog(EmployeeDetails employee, TimesheetCorrectionRequest request,
                               String action, String approverName, String remarks, LocalTime approvedLogout) {
        auditLogRepository.save(TimesheetAuditLog.builder()
                .employee(employee)
                .correctionRequestId(request.getId())
                .workDate(request.getWorkDate())
                .originalLogoutTime(null)
                .requestedLogoutTime(request.getRequestedLogoutTime())
                .approvedLogoutTime(approvedLogout)
                .approverName(approverName)
                .action(action)
                .actionDateTime(LocalDateTime.now())
                .remarks(remarks)
                .build());
    }

    private static String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) return "upload";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String saveAttachment(MultipartFile file, Long employeeId, LocalDate date) {
        try {
            Path dir = Paths.get(uploadDir, "corrections").toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String filename = employeeId + "_" + date + "_" + System.currentTimeMillis()
                    + "_" + sanitizeFilename(file.getOriginalFilename());
            Path dest = dir.resolve(filename).normalize();
            if (!dest.startsWith(dir)) throw new IllegalArgumentException("Invalid file name");
            Files.copy(file.getInputStream(), dest);
            return "corrections/" + filename;
        } catch (IOException e) {
            log.error("Failed to save correction attachment: {}", e.getMessage());
            return null;
        }
    }

    // ── Email dispatch ────────────────────────────────────────────────────────

    private void notifySubmission(EmployeeDetails employee, TimesheetCorrectionRequest request) {
        try {
            // Confirm to employee
            if (employee.getUser() != null) {
                emailService.sendCorrectionSubmittedToEmployee(
                        employee.getUser().getEmail(), employee.getFullName(),
                        request.getWorkDate(), request.getLoginTime(),
                        request.getRequestedLogoutTime(), request.getReason());
            }
            // Notify manager + HR
            String managerEmail = resolveManagerEmail(employee);
            String[] hrEmails   = resolveHrEmails(managerEmail);
            if (managerEmail != null) {
                emailService.sendCorrectionToManager(
                        managerEmail, hrEmails,
                        employee.getFullName(), employee.getEmployeeCode(), employee.getDepartment(),
                        request.getWorkDate(), request.getLoginTime(),
                        request.getRequestedLogoutTime(), request.getReason(), request.getId());
            }
        } catch (Exception e) {
            log.error("Failed to send correction submission emails: {}", e.getMessage());
        }
    }

    private void notifyApproval(EmployeeDetails employee, TimesheetCorrectionRequest request,
                                 String approverName, LocalTime approvedLogout) {
        try {
            if (employee.getUser() != null) {
                emailService.sendCorrectionApprovedToEmployee(
                        employee.getUser().getEmail(), employee.getFullName(),
                        request.getWorkDate(), approvedLogout,
                        approverName, request.getResolverComment());
            }
            if (hrNotificationsEnabled) {
                String[] hr = resolveHrEmails(null);
                if (hr.length > 0) {
                    emailService.sendCorrectionDecisionToHr(hr, "APPROVED",
                            employee.getFullName(), employee.getEmployeeCode(),
                            request.getWorkDate(), approvedLogout,
                            approverName, request.getResolverComment());
                }
            }
        } catch (Exception e) {
            log.error("Failed to send correction approval emails: {}", e.getMessage());
        }
    }

    private void notifyRejection(EmployeeDetails employee, TimesheetCorrectionRequest request,
                                  String rejectorName) {
        try {
            if (employee.getUser() != null) {
                emailService.sendCorrectionRejectedToEmployee(
                        employee.getUser().getEmail(), employee.getFullName(),
                        request.getWorkDate(), rejectorName, request.getResolverComment());
            }
            if (hrNotificationsEnabled) {
                String[] hr = resolveHrEmails(null);
                if (hr.length > 0) {
                    emailService.sendCorrectionDecisionToHr(hr, "REJECTED",
                            employee.getFullName(), employee.getEmployeeCode(),
                            request.getWorkDate(), null,
                            rejectorName, request.getResolverComment());
                }
            }
        } catch (Exception e) {
            log.error("Failed to send correction rejection emails: {}", e.getMessage());
        }
    }

    private String resolveManagerEmail(EmployeeDetails employee) {
        if (employee.getManager() != null && employee.getManager().getUser() != null)
            return employee.getManager().getUser().getEmail();
        return userRepository.findByRoleIn(java.util.List.of(Role.ADMIN, Role.DIRECTOR)).stream()
                .map(User::getEmail).filter(e -> e != null && !e.isBlank()).findFirst().orElse(null);
    }

    private String[] resolveHrEmails(String exclude) {
        return userRepository.findByRole(Role.HR).stream()
                .map(User::getEmail)
                .filter(e -> e != null && !e.isBlank()
                        && (exclude == null || !e.equalsIgnoreCase(exclude)))
                .distinct().toArray(String[]::new);
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private CorrectionRequestDTO toDTO(TimesheetCorrectionRequest r) {
        LocalTime firstLogin = sessionRepository
                .findByEmployeeIdAndWorkDateOrderByLoginTimeAsc(r.getEmployee().getId(), r.getWorkDate())
                .stream()
                .map(AttendanceSession::getLoginTime)
                .findFirst()
                .orElse(r.getLoginTime());

        return CorrectionRequestDTO.builder()
                .id(r.getId())
                .employeeId(r.getEmployee().getId())
                .employeeName(r.getEmployee().getFullName())
                .employeeCode(r.getEmployee().getEmployeeCode())
                .department(r.getEmployee().getDepartment())
                .sessionId(r.getSession().getId())
                .workDate(r.getWorkDate())
                .loginTime(r.getLoginTime())
                .firstLoginTime(firstLogin)
                .requestedLogoutTime(r.getRequestedLogoutTime())
                .reason(r.getReason())
                .attachmentPath(r.getAttachmentPath())
                .status(r.getStatus().name())
                .resolverComment(r.getResolverComment())
                .resolvedBy(r.getResolvedBy() != null ? r.getResolvedBy().getEmail() : null)
                .resolvedAt(r.getResolvedAt())
                .createdAt(r.getCreatedAt())
                .build();
    }

    private AuditLogDTO toAuditDTO(TimesheetAuditLog l) {
        return AuditLogDTO.builder()
                .id(l.getId())
                .employeeId(l.getEmployee().getId())
                .employeeName(l.getEmployee().getFullName())
                .correctionRequestId(l.getCorrectionRequestId())
                .workDate(l.getWorkDate())
                .originalLogoutTime(l.getOriginalLogoutTime())
                .requestedLogoutTime(l.getRequestedLogoutTime())
                .approvedLogoutTime(l.getApprovedLogoutTime())
                .approverName(l.getApproverName())
                .action(l.getAction())
                .actionDateTime(l.getActionDateTime())
                .remarks(l.getRemarks())
                .build();
    }

    private AttendanceSessionDTO toSessionDTO(AttendanceSession s) {
        LocalTime firstLogin = sessionRepository
                .findByEmployeeIdAndWorkDateOrderByLoginTimeAsc(s.getEmployee().getId(), s.getWorkDate())
                .stream()
                .map(AttendanceSession::getLoginTime)
                .findFirst()
                .orElse(s.getLoginTime());

        return AttendanceSessionDTO.builder()
                .id(s.getId())
                .employeeId(s.getEmployee().getId())
                .workDate(s.getWorkDate())
                .loginTime(s.getLoginTime())
                .firstLoginTime(firstLogin)
                .logoutTime(s.getLogoutTime())
                .sessionHours(s.getSessionHours())
                .status(s.getStatus() != null ? s.getStatus().name() : AttendanceSessionStatus.COMPLETE.name())
                .build();
    }
}
