package com.emp.management.service;

import com.emp.management.dto.CorrectionAuditLogDTO;
import com.emp.management.dto.JobDailySummaryDTO;
import com.emp.management.dto.WorkingHoursCorrectionRequestDTO;
import com.emp.management.dto.WorkingHoursCorrectionSubmitRequest;
import com.emp.management.entity.CorrectionReason;
import com.emp.management.entity.CorrectionRequestStatus;
import com.emp.management.entity.DailyAttendanceStatus;
import com.emp.management.entity.DayCorrectionStatus;
import com.emp.management.entity.EmployeeDetails;
import com.emp.management.entity.JobDailySummary;
import com.emp.management.entity.JobSessionBreak;
import com.emp.management.entity.JobWorkSession;
import com.emp.management.entity.Role;
import com.emp.management.entity.User;
import com.emp.management.entity.WorkingHoursCorrectionAuditLog;
import com.emp.management.entity.WorkingHoursCorrectionRequest;
import com.emp.management.exception.BadRequestException;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.EmployeeDetailsRepository;
import com.emp.management.repository.JobDailySummaryRepository;
import com.emp.management.repository.JobSessionBreakRepository;
import com.emp.management.repository.JobWorkSessionRepository;
import com.emp.management.repository.UserRepository;
import com.emp.management.repository.WorkingHoursCorrectionAuditLogRepository;
import com.emp.management.repository.WorkingHoursCorrectionRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkingHoursCorrectionService {

    private final WorkingHoursCorrectionRequestRepository correctionRequestRepository;
    private final WorkingHoursCorrectionAuditLogRepository auditLogRepository;
    private final CorrectionNotificationService correctionNotificationService;
    private final JobDailySummaryRepository summaryRepository;
    private final JobDailySummaryService jobDailySummaryService;
    private final JobWorkSessionRepository sessionRepository;
    private final JobSessionBreakRepository breakRepository;
    private final EmployeeDetailsRepository employeeDetailsRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final SystemSettingService systemSettingService;

    private static final List<CorrectionReason> BREAK_REASONS =
            List.of(CorrectionReason.FORGOT_START_BREAK, CorrectionReason.FORGOT_END_BREAK);

    @Transactional
    public WorkingHoursCorrectionRequestDTO submit(Long employeeId, WorkingHoursCorrectionSubmitRequest req, String callerEmail) {
        requireSelfOrPrivileged(employeeId, callerEmail);
        EmployeeDetails employee = findEmployee(employeeId);

        LocalDate workDate = req.getWorkDate();
        if (workDate.isAfter(LocalDate.now())) {
            throw new BadRequestException("Cannot submit a correction for a future date");
        }
        if (correctionRequestRepository.existsByEmployeeIdAndWorkDateAndStatus(
                employeeId, workDate, CorrectionRequestStatus.PENDING)) {
            throw new BadRequestException("A correction request is already pending for this date");
        }

        CorrectionReason reason = req.getReason();
        boolean isBreakReason = BREAK_REASONS.contains(reason);

        JobWorkSession targetSession = null;
        if (isBreakReason) {
            if (req.getRequestedBreakStartTime() == null || req.getRequestedBreakEndTime() == null) {
                throw new BadRequestException("Break start time and end time are required for this reason");
            }
            if (!req.getRequestedBreakEndTime().isAfter(req.getRequestedBreakStartTime())) {
                throw new BadRequestException("Break end time must be after break start time");
            }
            List<JobWorkSession> sessions = sessionRepository
                    .findByEmployeeIdAndWorkDateOrderByLoginTimeAsc(employeeId, workDate);
            targetSession = resolveSessionForBreak(sessions, req.getRequestedBreakStartTime(), req.getRequestedBreakEndTime());
        } else if (reason == CorrectionReason.APPROVED_OVERTIME) {
            if (req.getOvertimeRemarks() == null || req.getOvertimeRemarks().isBlank()) {
                throw new BadRequestException("Overtime remarks are required for Approved Overtime");
            }
        } else if (reason == CorrectionReason.OTHER) {
            if (req.getReasonComments() == null || req.getReasonComments().isBlank()) {
                throw new BadRequestException("Comments are required when reason is Other");
            }
        }

        JobDailySummaryDTO original = jobDailySummaryService.getForDate(employeeId, workDate);
        if (original == null) {
            throw new BadRequestException("No timesheet data found for this date");
        }

        int required = systemSettingService.getInt(SystemSettingService.KEY_CORR_OVERTIME_THRESHOLD_MINUTES, 480);

        WorkingHoursCorrectionRequest.WorkingHoursCorrectionRequestBuilder builder = WorkingHoursCorrectionRequest.builder()
                .employee(employee)
                .workDate(workDate)
                .jobWorkSession(targetSession)
                .reason(reason)
                .reasonComments(req.getReasonComments())
                .requestedBreakStartTime(req.getRequestedBreakStartTime())
                .requestedBreakEndTime(req.getRequestedBreakEndTime())
                .overtimeRemarks(req.getOvertimeRemarks())
                .originalWorkingMinutes(original.getTotalWorkingMinutes())
                .originalBreakMinutes(original.getTotalBreakMinutes())
                .originalOfficeMinutes(original.getTotalOfficeMinutes())
                .originalOvertimeMinutes(original.getOvertimeMinutes())
                .originalStatus(DailyAttendanceStatus.valueOf(original.getStatus()))
                .status(CorrectionRequestStatus.PENDING);

        if (isBreakReason) {
            List<JobWorkSession> sessions = sessionRepository
                    .findByEmployeeIdAndWorkDateOrderByLoginTimeAsc(employeeId, workDate);
            int breakMinutes = (int) ChronoUnit.MINUTES.between(
                    req.getRequestedBreakStartTime(), req.getRequestedBreakEndTime());
            Map<Long, Integer> override = Map.of(targetSession.getId(), breakMinutes);
            JobDailySummaryService.Computed preview = jobDailySummaryService
                    .computeSummary(employee, workDate, sessions, LocalDateTime.now(), override);
            int officeMinutes = preview.totalWorkingMinutes() + preview.totalBreakMinutes();
            int overtimeMinutes = Math.max(0, preview.totalWorkingMinutes() - required);
            builder.requestedWorkingMinutes(preview.totalWorkingMinutes())
                    .requestedBreakMinutes(preview.totalBreakMinutes())
                    .requestedOfficeMinutes(officeMinutes)
                    .requestedOvertimeMinutes(overtimeMinutes)
                    .requestedStatus(preview.status());
        } else {
            // No raw data change for these reasons — the requested figures mirror the
            // original ones; only correctionStatus moves once a manager decides.
            builder.requestedWorkingMinutes(original.getTotalWorkingMinutes())
                    .requestedBreakMinutes(original.getTotalBreakMinutes())
                    .requestedOfficeMinutes(original.getTotalOfficeMinutes())
                    .requestedOvertimeMinutes(original.getOvertimeMinutes())
                    .requestedStatus(DailyAttendanceStatus.valueOf(original.getStatus()));
        }

        WorkingHoursCorrectionRequest saved = correctionRequestRepository.save(builder.build());

        JobDailySummary summary = summaryRepository.findByEmployeeIdAndWorkDate(employeeId, workDate)
                .orElseGet(() -> jobDailySummaryService.generateForEmployee(employee, workDate));
        summary.setCorrectionStatus(DayCorrectionStatus.PENDING);
        summary.setLatestCorrectionRequestId(saved.getId());
        summaryRepository.save(summary);

        StringBuilder details = new StringBuilder()
                .append("Original: ").append(formatMinutes(saved.getOriginalWorkingMinutes()))
                .append(" (").append(saved.getOriginalStatus()).append(") ")
                .append("→ Requested: ").append(formatMinutes(saved.getRequestedWorkingMinutes()))
                .append(" (").append(saved.getRequestedStatus()).append(") ")
                .append("— Reason: ").append(reason.name().replace("_", " "));
        if (isBreakReason) {
            DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("hh:mm a");
            details.append(" — Requested break: ")
                    .append(req.getRequestedBreakStartTime().format(timeFmt))
                    .append(" – ")
                    .append(req.getRequestedBreakEndTime().format(timeFmt));
        } else if (reason == CorrectionReason.APPROVED_OVERTIME && req.getOvertimeRemarks() != null) {
            details.append(" — Overtime: ").append(req.getOvertimeRemarks());
        }

        auditLogRepository.save(WorkingHoursCorrectionAuditLog.builder()
                .correctionRequest(saved)
                .performedBy(employee)
                .action("SUBMITTED")
                .details(details.toString())
                .build());

        try {
            String managerEmail = resolveManagerEmail(employee);
            if (managerEmail != null) {
                String[] cc = collectHrAdminEmails(managerEmail);
                emailService.sendCorrectionRequestEmail(
                        managerEmail, cc, employee.getFullName(), employee.getEmployeeCode(),
                        workDate, reason.name(), req.getReasonComments(),
                        formatMinutes(saved.getOriginalWorkingMinutes()), formatMinutes(saved.getOriginalBreakMinutes()),
                        formatMinutes(saved.getOriginalOfficeMinutes()), formatMinutes(saved.getOriginalOvertimeMinutes()),
                        saved.getOriginalStatus().name(),
                        formatMinutes(saved.getRequestedWorkingMinutes()), formatMinutes(saved.getRequestedBreakMinutes()),
                        formatMinutes(saved.getRequestedOfficeMinutes()), formatMinutes(saved.getRequestedOvertimeMinutes()),
                        saved.getRequestedStatus().name(),
                        req.getRequestedBreakStartTime(), req.getRequestedBreakEndTime(), req.getOvertimeRemarks());

                employeeDetailsRepository.findByUserEmail(managerEmail).ifPresent(manager ->
                        correctionNotificationService.notify(saved, manager, employee.getFullName(),
                                employee.getFullName() + " submitted a Working Hours Correction Request for " + workDate));
            }
        } catch (Exception e) {
            log.error("Correction request email/notification failed for employee {} — request {} is saved: {}",
                    employeeId, saved.getId(), e.getMessage());
        }

        return toDTO(saved);
    }

    @Transactional
    public WorkingHoursCorrectionRequestDTO decide(Long requestId, String managerEmail, CorrectionRequestStatus decision, String comment) {
        if (decision != CorrectionRequestStatus.APPROVED && decision != CorrectionRequestStatus.REJECTED) {
            throw new BadRequestException("Invalid decision: " + decision);
        }
        WorkingHoursCorrectionRequest request = correctionRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkingHoursCorrectionRequest", requestId));

        if (request.getStatus() != CorrectionRequestStatus.PENDING) {
            throw new BadRequestException("Correction request already processed");
        }

        EmployeeDetails manager = employeeDetailsRepository.findByUserEmail(managerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found for: " + managerEmail));

        EmployeeDetails employee = request.getEmployee();
        LocalDate workDate = request.getWorkDate();

        JobDailySummary summary = summaryRepository.findByEmployeeIdAndWorkDate(employee.getId(), workDate)
                .orElseGet(() -> jobDailySummaryService.generateForEmployee(employee, workDate));

        if (decision == CorrectionRequestStatus.APPROVED) {
            boolean isBreakReason = BREAK_REASONS.contains(request.getReason());
            if (isBreakReason && request.getJobWorkSession() != null) {
                JobWorkSession session = request.getJobWorkSession();
                JobSessionBreak breakRow = breakRepository.findByJobWorkSessionIdAndBreakEndTimeIsNull(session.getId())
                        .orElseGet(() -> JobSessionBreak.builder().jobWorkSession(session).build());
                breakRow.setBreakStartTime(request.getRequestedBreakStartTime());
                breakRow.setBreakEndTime(request.getRequestedBreakEndTime());
                breakRow.setBreakMinutes((int) ChronoUnit.MINUTES.between(
                        request.getRequestedBreakStartTime(), request.getRequestedBreakEndTime()));
                breakRepository.save(breakRow);

                // Recompute through the real pipeline — the same one the nightly rollup
                // uses — so Working Hours/Break/Office/Overtime/Status all come from a
                // single source of truth, not a second calculation here.
                summary = jobDailySummaryService.generateForEmployee(employee, workDate);
            }

            int required = systemSettingService.getInt(SystemSettingService.KEY_CORR_OVERTIME_THRESHOLD_MINUTES, 480);
            request.setFinalWorkingMinutes(summary.getTotalWorkingMinutes());
            request.setFinalBreakMinutes(summary.getTotalBreakMinutes());
            request.setFinalOfficeMinutes(summary.getTotalWorkingMinutes() + summary.getTotalBreakMinutes());
            request.setFinalOvertimeMinutes(Math.max(0, summary.getTotalWorkingMinutes() - required));
            request.setFinalStatus(summary.getStatus());

            summary.setCorrectionStatus(DayCorrectionStatus.APPROVED);
        } else {
            summary.setCorrectionStatus(DayCorrectionStatus.REJECTED);
        }
        summary.setLatestCorrectionRequestId(request.getId());
        summaryRepository.save(summary);

        request.setStatus(decision);
        request.setApprovedBy(manager);
        request.setManagerComment(comment);
        request.setActionDate(LocalDateTime.now());
        WorkingHoursCorrectionRequest saved = correctionRequestRepository.save(request);

        auditLogRepository.save(WorkingHoursCorrectionAuditLog.builder()
                .correctionRequest(saved)
                .performedBy(manager)
                .action(decision.name())
                .details(decision == CorrectionRequestStatus.APPROVED
                        ? "Approved — Final: " + formatMinutes(saved.getFinalWorkingMinutes()) + " (" + saved.getFinalStatus() + "). Comment: " + comment
                        : "Rejected. Comment: " + comment)
                .build());

        try {
            String employeeEmail = employee.getUser() != null ? employee.getUser().getEmail() : null;
            if (employeeEmail != null) {
                String[] cc = collectHrAdminEmails(employeeEmail);
                boolean approved = decision == CorrectionRequestStatus.APPROVED;
                emailService.sendCorrectionDecisionEmail(
                        employeeEmail, cc, employee.getFullName(), decision.name(), workDate,
                        manager.getFullName(), comment,
                        approved ? formatMinutes(saved.getFinalWorkingMinutes()) : null,
                        approved ? formatMinutes(saved.getFinalBreakMinutes()) : null,
                        approved ? formatMinutes(saved.getFinalOfficeMinutes()) : null,
                        approved ? formatMinutes(saved.getFinalOvertimeMinutes()) : null,
                        approved && saved.getFinalStatus() != null ? saved.getFinalStatus().name() : null);

                correctionNotificationService.notify(saved, employee, manager.getFullName(),
                        "Your Working Hours Correction Request for " + workDate + " was " + decision.name().toLowerCase());
            }
        } catch (Exception e) {
            log.error("Correction decision email/notification failed for request {} — status {} is saved: {}",
                    requestId, decision, e.getMessage());
        }

        return toDTO(saved);
    }

    public List<WorkingHoursCorrectionRequestDTO> getMyRequests(Long employeeId, String callerEmail) {
        requireSelfOrPrivileged(employeeId, callerEmail);
        return correctionRequestRepository.findByEmployeeIdOrderBySubmittedAtDesc(employeeId).stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    public List<WorkingHoursCorrectionRequestDTO> getPendingForManager(Long managerId) {
        return correctionRequestRepository.findPendingForManager(managerId).stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    public List<WorkingHoursCorrectionRequestDTO> getAllForManager(Long managerId) {
        return correctionRequestRepository.findAllForManager(managerId).stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    public List<WorkingHoursCorrectionRequestDTO> getAll() {
        return correctionRequestRepository.findAllOrderByPendingFirst().stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    public List<CorrectionAuditLogDTO> getAuditTrail(Long requestId, String callerEmail) {
        WorkingHoursCorrectionRequest request = correctionRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkingHoursCorrectionRequest", requestId));
        requireSelfManagerOrPrivileged(request, callerEmail);
        return auditLogRepository.findByCorrectionRequestIdOrderByCreatedAtAsc(requestId).stream()
                .map(this::toAuditDTO).collect(Collectors.toList());
    }

    private JobWorkSession resolveSessionForBreak(List<JobWorkSession> sessions, LocalDateTime breakStart, LocalDateTime breakEnd) {
        if (sessions.isEmpty()) {
            throw new BadRequestException("No recorded session found for this date");
        }
        if (sessions.size() == 1) {
            return sessions.get(0);
        }
        return sessions.stream()
                .filter(s -> s.getLoginTime() != null && !breakStart.isBefore(s.getLoginTime())
                        && (s.getLogoutTime() == null || !breakEnd.isAfter(s.getLogoutTime())))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Break time does not fall within any recorded session"));
    }

    private String formatMinutes(Integer minutes) {
        if (minutes == null) return "—";
        int h = minutes / 60;
        int m = minutes % 60;
        return h + "h " + m + "m";
    }

    private String[] collectHrAdminEmails(String excludeEmail) {
        return Stream.concat(
                userRepository.findByRole(Role.HR).stream(),
                userRepository.findByRoleIn(List.of(Role.ADMIN, Role.DIRECTOR)).stream()
        )
        .map(User::getEmail)
        .filter(e -> e != null && !e.equalsIgnoreCase(excludeEmail))
        .distinct()
        .toArray(String[]::new);
    }

    private String resolveManagerEmail(EmployeeDetails employee) {
        if (employee.getManager() != null && employee.getManager().getUser() != null) {
            return employee.getManager().getUser().getEmail();
        }
        return userRepository.findByRoleIn(List.of(Role.ADMIN, Role.DIRECTOR)).stream()
                .map(User::getEmail)
                .findFirst()
                .orElse(null);
    }

    private void requireSelfOrPrivileged(Long targetEmployeeId, String callerEmail) {
        EmployeeDetails caller = employeeDetailsRepository.findByUserEmail(callerEmail).orElse(null);
        if (caller == null) return;
        Role role = caller.getUser() != null ? caller.getUser().getRole() : null;
        boolean privileged = role == Role.ADMIN || role == Role.DIRECTOR || role == Role.HR
                          || role == Role.MANAGER || role == Role.ASSISTANT_MANAGER;
        if (!privileged && !caller.getId().equals(targetEmployeeId)) {
            throw new AccessDeniedException("Access denied");
        }
    }

    private void requireSelfManagerOrPrivileged(WorkingHoursCorrectionRequest request, String callerEmail) {
        EmployeeDetails caller = employeeDetailsRepository.findByUserEmail(callerEmail).orElse(null);
        if (caller == null) return;
        Role role = caller.getUser() != null ? caller.getUser().getRole() : null;
        boolean privileged = role == Role.ADMIN || role == Role.DIRECTOR || role == Role.HR;
        boolean isSelf = caller.getId().equals(request.getEmployee().getId());
        boolean isManager = request.getEmployee().getManager() != null
                && caller.getId().equals(request.getEmployee().getManager().getId());
        if (!privileged && !isSelf && !isManager) {
            throw new AccessDeniedException("Access denied");
        }
    }

    private EmployeeDetails findEmployee(Long id) {
        return employeeDetailsRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", id));
    }

    private WorkingHoursCorrectionRequestDTO toDTO(WorkingHoursCorrectionRequest r) {
        EmployeeDetails emp = r.getEmployee();
        EmployeeDetails mgr = emp.getManager();
        return WorkingHoursCorrectionRequestDTO.builder()
                .id(r.getId())
                .employeeId(emp.getId())
                .employeeName(emp.getFullName())
                .employeeCode(emp.getEmployeeCode())
                .department(emp.getDepartment())
                .managerName(mgr != null ? mgr.getFullName() : null)
                .workDate(r.getWorkDate())
                .reason(r.getReason())
                .reasonComments(r.getReasonComments())
                .requestedBreakStartTime(r.getRequestedBreakStartTime())
                .requestedBreakEndTime(r.getRequestedBreakEndTime())
                .overtimeRemarks(r.getOvertimeRemarks())
                .originalWorkingMinutes(r.getOriginalWorkingMinutes())
                .originalBreakMinutes(r.getOriginalBreakMinutes())
                .originalOfficeMinutes(r.getOriginalOfficeMinutes())
                .originalOvertimeMinutes(r.getOriginalOvertimeMinutes())
                .originalStatus(r.getOriginalStatus() != null ? r.getOriginalStatus().name() : null)
                .requestedWorkingMinutes(r.getRequestedWorkingMinutes())
                .requestedBreakMinutes(r.getRequestedBreakMinutes())
                .requestedOfficeMinutes(r.getRequestedOfficeMinutes())
                .requestedOvertimeMinutes(r.getRequestedOvertimeMinutes())
                .requestedStatus(r.getRequestedStatus() != null ? r.getRequestedStatus().name() : null)
                .finalWorkingMinutes(r.getFinalWorkingMinutes())
                .finalBreakMinutes(r.getFinalBreakMinutes())
                .finalOfficeMinutes(r.getFinalOfficeMinutes())
                .finalOvertimeMinutes(r.getFinalOvertimeMinutes())
                .finalStatus(r.getFinalStatus() != null ? r.getFinalStatus().name() : null)
                .status(r.getStatus())
                .approvedByName(r.getApprovedBy() != null ? r.getApprovedBy().getFullName() : null)
                .managerComment(r.getManagerComment())
                .submittedAt(r.getSubmittedAt())
                .actionDate(r.getActionDate())
                .build();
    }

    private CorrectionAuditLogDTO toAuditDTO(WorkingHoursCorrectionAuditLog log) {
        return CorrectionAuditLogDTO.builder()
                .id(log.getId())
                .action(log.getAction())
                .details(log.getDetails())
                .performedByName(log.getPerformedBy() != null ? log.getPerformedBy().getFullName() : null)
                .createdAt(log.getCreatedAt())
                .build();
    }
}
