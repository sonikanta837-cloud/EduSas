package com.emp.management.service;

import com.emp.management.dto.JobSessionBreakDTO;
import com.emp.management.dto.JobSessionLoginRequest;
import com.emp.management.dto.JobSessionTodayDTO;
import com.emp.management.dto.JobWorkSessionDTO;
import com.emp.management.dto.TimesheetMasterValueDTO;
import com.emp.management.dto.WorkReportDTO;
import com.emp.management.entity.*;
import com.emp.management.exception.BadRequestException;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.EmployeeDetailsRepository;
import com.emp.management.repository.JobBreakAlertLogRepository;
import com.emp.management.repository.JobSessionBreakRepository;
import com.emp.management.repository.JobWorkSessionRepository;
import com.emp.management.repository.TimesheetMasterValueRepository;
import com.emp.management.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobWorkSessionService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final JobWorkSessionRepository sessionRepository;
    private final JobSessionBreakRepository breakRepository;
    private final JobBreakAlertLogRepository breakAlertLogRepository;
    private final TimesheetMasterValueRepository masterValueRepository;
    private final EmployeeDetailsRepository employeeDetailsRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${app.job-break-alert.threshold-minutes:60}")
    private int breakThresholdMinutes;

    @Transactional
    public JobWorkSessionDTO login(JobSessionLoginRequest req) {
        if (sessionRepository.findByEmployeeIdAndOpenFlagTrue(req.getEmployeeId()).isPresent()) {
            throw new BadRequestException("You already have an active job session — please log out before starting a new one");
        }

        EmployeeDetails employee = employeeDetailsRepository.findById(req.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee", req.getEmployeeId()));

        TimesheetMasterValue client   = requireActive(req.getClientId(), MasterDataType.CLIENT);
        TimesheetMasterValue job      = requireActive(req.getJobId(), MasterDataType.JOB);
        TimesheetMasterValue jobType  = requireActive(req.getJobTypeId(), MasterDataType.JOB_TYPE);
        TimesheetMasterValue periodEnd = requireActive(req.getPeriodEndId(), MasterDataType.PERIOD_END);

        LocalDateTime now = LocalDateTime.now(ZONE);

        JobWorkSession session = sessionRepository.save(JobWorkSession.builder()
                .employee(employee)
                .client(client)
                .job(job)
                .jobType(jobType)
                .periodEnd(periodEnd)
                .workDate(now.toLocalDate())
                .description(req.getDescription())
                .loginTime(now)
                .breakAlertSent(false)
                .openFlag(Boolean.TRUE)
                .status(JobSessionStatus.OPEN)
                .build());

        return toDTO(session);
    }

    @Transactional
    public JobWorkSessionDTO logout(Long employeeId) {
        JobWorkSession session = sessionRepository.findByEmployeeIdAndOpenFlagTrue(employeeId)
                .orElseThrow(() -> new BadRequestException("No active job session found"));

        LocalDateTime now = LocalDateTime.now(ZONE);

        // Safety net: the Job Task and Break controls are independent in the UI, so a
        // user can log out while still on break. Auto-close any dangling open break
        // at logout time rather than leaving it open forever or blocking the logout.
        breakRepository.findByJobWorkSessionIdAndBreakEndTimeIsNull(session.getId()).ifPresent(openBreak -> {
            openBreak.setBreakEndTime(now);
            openBreak.setBreakMinutes((int) ChronoUnit.MINUTES.between(openBreak.getBreakStartTime(), now));
            breakRepository.save(openBreak);
        });

        session.setLogoutTime(now);
        session.setSessionMinutes((int) ChronoUnit.MINUTES.between(session.getLoginTime(), now));
        session.setOpenFlag(null);
        session.setStatus(JobSessionStatus.CLOSED);

        return toDTO(sessionRepository.save(session));
    }

    @Transactional
    public JobWorkSessionDTO switchJob(JobSessionLoginRequest req) {
        JobWorkSession current = sessionRepository.findByEmployeeIdAndOpenFlagTrue(req.getEmployeeId())
                .orElseThrow(() -> new BadRequestException("No active job session to switch from"));

        LocalDateTime now = LocalDateTime.now(ZONE);

        breakRepository.findByJobWorkSessionIdAndBreakEndTimeIsNull(current.getId()).ifPresent(openBreak -> {
            openBreak.setBreakEndTime(now);
            openBreak.setBreakMinutes((int) ChronoUnit.MINUTES.between(openBreak.getBreakStartTime(), now));
            breakRepository.save(openBreak);
        });

        current.setLogoutTime(now);
        current.setSessionMinutes((int) ChronoUnit.MINUTES.between(current.getLoginTime(), now));
        current.setOpenFlag(null);
        current.setStatus(JobSessionStatus.CLOSED);
        // Flush the close before inserting the new open session — Hibernate's default
        // flush order runs inserts before updates, which would otherwise momentarily
        // violate the (employee_id, open_flag) unique constraint.
        sessionRepository.saveAndFlush(current);

        TimesheetMasterValue client    = requireActive(req.getClientId(), MasterDataType.CLIENT);
        TimesheetMasterValue job       = requireActive(req.getJobId(), MasterDataType.JOB);
        TimesheetMasterValue jobType   = requireActive(req.getJobTypeId(), MasterDataType.JOB_TYPE);
        TimesheetMasterValue periodEnd = requireActive(req.getPeriodEndId(), MasterDataType.PERIOD_END);

        JobWorkSession newSession = sessionRepository.save(JobWorkSession.builder()
                .employee(current.getEmployee())
                .client(client)
                .job(job)
                .jobType(jobType)
                .periodEnd(periodEnd)
                .workDate(now.toLocalDate())
                .description(req.getDescription())
                .loginTime(now)
                .breakAlertSent(false)
                .openFlag(Boolean.TRUE)
                .status(JobSessionStatus.OPEN)
                .build());

        return toDTO(newSession);
    }

    @Transactional
    public JobWorkSessionDTO startBreak(Long employeeId) {
        JobWorkSession session = sessionRepository.findByEmployeeIdAndOpenFlagTrue(employeeId)
                .orElseThrow(() -> new BadRequestException("No active job session found — log in before starting a break"));

        if (breakRepository.findByJobWorkSessionIdAndBreakEndTimeIsNull(session.getId()).isPresent()) {
            throw new BadRequestException("A break is already in progress");
        }

        breakRepository.save(JobSessionBreak.builder()
                .jobWorkSession(session)
                .breakStartTime(LocalDateTime.now(ZONE))
                .build());

        return toDTO(session);
    }

    @Transactional
    public JobWorkSessionDTO endBreak(Long employeeId) {
        JobWorkSession session = sessionRepository.findByEmployeeIdAndOpenFlagTrue(employeeId)
                .orElseThrow(() -> new BadRequestException("No active job session found"));

        JobSessionBreak openBreak = breakRepository.findByJobWorkSessionIdAndBreakEndTimeIsNull(session.getId())
                .orElseThrow(() -> new BadRequestException("No active break found"));

        LocalDateTime now = LocalDateTime.now(ZONE);
        openBreak.setBreakEndTime(now);
        openBreak.setBreakMinutes((int) ChronoUnit.MINUTES.between(openBreak.getBreakStartTime(), now));
        breakRepository.save(openBreak);

        return toDTO(session);
    }

    @Transactional(readOnly = true)
    public JobSessionTodayDTO getToday(Long employeeId) {
        LocalDate today = LocalDate.now(ZONE);
        List<JobWorkSession> sessions = sessionRepository
                .findByEmployeeIdAndWorkDateOrderByLoginTimeAsc(employeeId, today);

        int totalMinutes = sessions.stream()
                .filter(s -> s.getSessionMinutes() != null)
                .mapToInt(JobWorkSession::getSessionMinutes)
                .sum();

        // Not scoped to `today` — an open session started on a previous day (forgotten
        // logout) must still surface here, otherwise this view disagrees with the
        // global openFlag check that login()/logout()/switchJob() enforce, leaving the
        // UI showing "Login" enabled while the backend rejects it as already open.
        JobWorkSession open = sessionRepository.findByEmployeeIdAndOpenFlagTrue(employeeId).orElse(null);

        return JobSessionTodayDTO.builder()
                .sessions(sessions.stream().map(this::toDTO).collect(Collectors.toList()))
                .totalMinutesToday(totalMinutes)
                .totalFormatted(formatMinutes(totalMinutes))
                .openSession(open != null ? toDTO(open) : null)
                .build();
    }

    @Transactional(readOnly = true)
    public List<JobWorkSessionDTO> getRange(Long employeeId, LocalDate start, LocalDate end) {
        return sessionRepository
                .findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAscLoginTimeAsc(employeeId, start, end)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<WorkReportDTO> getWorkReport(LocalDate start, LocalDate end) {
        LocalDateTime now = LocalDateTime.now(ZONE);
        return sessionRepository.findAllForWorkReport(start, end).stream().map(s -> {
            var emp = s.getEmployee();
            String mgr = (emp.getManager() != null) ? emp.getManager().getFullName() : "—";

            Integer minutes = s.getSessionMinutes();
            if (minutes == null && s.getLogoutTime() == null) {
                minutes = (int) ChronoUnit.MINUTES.between(s.getLoginTime(), now);
            }
            double hours = minutes != null ? Math.round(minutes / 60.0 * 100.0) / 100.0 : 0.0;

            return WorkReportDTO.builder()
                    .entryId(s.getId())
                    .employeeId(emp.getId())
                    .employeeCode(emp.getEmployeeCode() != null ? emp.getEmployeeCode() : "—")
                    .employeeName(emp.getFullName())
                    .department(emp.getDepartment() != null ? emp.getDepartment() : "—")
                    .location(emp.getSeatingLocation() != null ? emp.getSeatingLocation() : "—")
                    .managerName(mgr)
                    .date(s.getWorkDate())
                    .projectName(s.getJob() != null ? s.getJob().getValue() : "—")
                    .taskName(s.getDescription() != null && !s.getDescription().isBlank() ? s.getDescription() : "—")
                    .hours(hours)
                    .loginTime(s.getLoginTime())
                    .logoutTime(s.getLogoutTime())
                    .build();
        }).collect(Collectors.toList());
    }

    // ── Post-logout gap monitor: polls every 5 minutes for the time between a job
    //    logout and the next login exceeding 60 minutes. Distinct from the explicit
    //    in-session Break Start/End feature monitored by monitorActiveBreaks() below. ──

    @Scheduled(fixedRate = 5 * 60 * 1000)
    @Transactional
    public void monitorBreaks() {
        LocalDateTime now = LocalDateTime.now(ZONE);

        for (EmployeeDetails emp : employeeDetailsRepository.findByActive(true)) {
            try {
                if (emp.getUser() == null) continue;
                if (sessionRepository.findByEmployeeIdAndOpenFlagTrue(emp.getId()).isPresent()) continue;

                JobWorkSession lastClosed = sessionRepository
                        .findTopByEmployeeIdAndLogoutTimeIsNotNullOrderByLogoutTimeDesc(emp.getId())
                        .orElse(null);
                if (lastClosed == null || lastClosed.isBreakAlertSent()) continue;

                long minutesSinceLogout = ChronoUnit.MINUTES.between(lastClosed.getLogoutTime(), now);
                if (minutesSinceLogout <= breakThresholdMinutes) continue;

                dispatchBreakAlert(emp, lastClosed, minutesSinceLogout);

                lastClosed.setBreakAlertSent(true);
                sessionRepository.save(lastClosed);
            } catch (Exception e) {
                log.warn("Job break monitor failed for employee {}: {}", emp.getFullName(), e.getMessage());
            }
        }
    }

    // ── Active break monitor: polls every minute for explicit in-session breaks
    //    (Break Start/End) still open past the threshold. Alerts the employee's
    //    reporting manager immediately (within one poll cycle) and only once per
    //    break — guarded by JobSessionBreak.alertSent so a break that continues
    //    well past 60 minutes never triggers a second email. ──

    @Scheduled(fixedRate = 60 * 1000)
    @Transactional
    public void monitorActiveBreaks() {
        LocalDateTime now = LocalDateTime.now(ZONE);

        for (JobSessionBreak openBreak : breakRepository.findByBreakEndTimeIsNullAndAlertSentFalse()) {
            try {
                long minutesOnBreak = ChronoUnit.MINUTES.between(openBreak.getBreakStartTime(), now);
                if (minutesOnBreak <= breakThresholdMinutes) continue;

                JobWorkSession session = openBreak.getJobWorkSession();
                EmployeeDetails emp = session.getEmployee();
                if (emp == null || emp.getUser() == null) continue;

                dispatchActiveBreakAlert(emp, session, openBreak, minutesOnBreak, now);

                openBreak.setAlertSent(true);
                breakRepository.save(openBreak);
            } catch (Exception e) {
                log.warn("Active break monitor failed for break {}: {}", openBreak.getId(), e.getMessage());
            }
        }
    }

    private void dispatchActiveBreakAlert(EmployeeDetails emp, JobWorkSession session, JobSessionBreak openBreak,
                                           long minutesOnBreak, LocalDateTime now) {
        String selfEmail = emp.getUser() != null ? emp.getUser().getEmail() : null;

        String managerEmail = (emp.getManager() != null && emp.getManager().getUser() != null)
                ? emp.getManager().getUser().getEmail() : null;
        if (managerEmail == null) {
            managerEmail = userRepository.findByRoleIn(List.of(Role.ADMIN, Role.DIRECTOR)).stream()
                    .map(User::getEmail)
                    .filter(e -> e != null && !e.isBlank() && !e.equalsIgnoreCase(selfEmail))
                    .findFirst().orElse(null);
        }
        if (managerEmail == null) {
            log.warn("No manager or admin found for active break alert — employee: {}", emp.getFullName());
            return;
        }

        String[] to = Stream.of(managerEmail, selfEmail)
                .filter(e -> e != null && !e.isBlank())
                .distinct()
                .toArray(String[]::new);

        String[] cc = Stream.concat(
                userRepository.findByRole(Role.HR).stream(),
                userRepository.findByRoleIn(List.of(Role.ADMIN, Role.DIRECTOR)).stream()
        )
        .map(User::getEmail)
        .filter(e -> e != null && !e.isBlank() && Arrays.stream(to).noneMatch(e::equalsIgnoreCase))
        .distinct()
        .toArray(String[]::new);

        String client  = session.getClient() != null ? session.getClient().getValue() : "—";
        String jobName = session.getJob() != null ? session.getJob().getValue() : "—";
        String jobTask = session.getDescription();
        String durationStr = formatMinutes((int) minutesOnBreak);

        emailService.sendJobBreakAlertEmail(
                to, cc,
                emp.getFullName(),
                emp.getEmployeeCode(),
                emp.getDepartment(),
                client, jobName, jobTask,
                openBreak.getBreakStartTime(),
                durationStr,
                session.getWorkDate()
        );

        breakAlertLogRepository.save(JobBreakAlertLog.builder()
                .employee(emp)
                .jobWorkSessionId(session.getId())
                .jobSessionBreakId(openBreak.getId())
                .employeeName(emp.getFullName())
                .employeeCode(emp.getEmployeeCode())
                .department(emp.getDepartment())
                .client(client)
                .jobName(jobName)
                .jobTask(jobTask)
                .breakStartTime(openBreak.getBreakStartTime())
                .breakDurationMinutes((int) minutesOnBreak)
                .recipientEmails(String.join(", ", to))
                .sentAt(now)
                .build());

        log.info("Active break alert sent to {} (cc: {}) for {} — on break since {} ({} min)",
                Arrays.toString(to), Arrays.toString(cc), emp.getFullName(), openBreak.getBreakStartTime(), minutesOnBreak);
    }

    private void dispatchBreakAlert(EmployeeDetails emp, JobWorkSession lastClosed, long minutesSinceLogout) {
        String selfEmail = emp.getUser() != null ? emp.getUser().getEmail() : null;

        String managerEmail = (emp.getManager() != null && emp.getManager().getUser() != null)
                ? emp.getManager().getUser().getEmail() : null;
        if (managerEmail == null) {
            managerEmail = userRepository.findByRoleIn(List.of(Role.ADMIN, Role.DIRECTOR)).stream()
                    .map(User::getEmail)
                    .filter(e -> e != null && !e.isBlank() && !e.equalsIgnoreCase(selfEmail))
                    .findFirst().orElse(null);
        }
        if (managerEmail == null) {
            log.warn("No manager or admin found for job break alert — employee: {}", emp.getFullName());
            return;
        }

        String[] to = Stream.of(managerEmail, selfEmail)
                .filter(e -> e != null && !e.isBlank())
                .distinct()
                .toArray(String[]::new);

        String[] cc = Stream.concat(
                userRepository.findByRole(Role.HR).stream(),
                userRepository.findByRoleIn(List.of(Role.ADMIN, Role.DIRECTOR)).stream()
        )
        .map(User::getEmail)
        .filter(e -> e != null && !e.isBlank() && Arrays.stream(to).noneMatch(e::equalsIgnoreCase))
        .distinct()
        .toArray(String[]::new);

        emailService.sendJobBreakAlertEmail(
                to, cc,
                emp.getFullName(),
                emp.getEmployeeCode(),
                emp.getDepartment(),
                lastClosed.getClient() != null ? lastClosed.getClient().getValue() : "—",
                lastClosed.getJob() != null ? lastClosed.getJob().getValue() : "—",
                lastClosed.getDescription(),
                lastClosed.getLogoutTime(),
                formatMinutes((int) minutesSinceLogout),
                lastClosed.getWorkDate()
        );

        log.info("Job break alert sent to {} (cc: {}) for {} — inactive since {}",
                Arrays.toString(to), Arrays.toString(cc), emp.getFullName(), lastClosed.getLogoutTime());
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private TimesheetMasterValue requireActive(Long id, MasterDataType expectedType) {
        TimesheetMasterValue value = masterValueRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Master value", id));
        if (value.getType() != expectedType) {
            throw new BadRequestException("Selected " + expectedType.name().toLowerCase() + " value is invalid");
        }
        if (!value.isActive()) {
            throw new BadRequestException("The selected " + expectedType.name().toLowerCase().replace('_', ' ') +
                    " value is no longer active — please choose another");
        }
        return value;
    }

    private String formatMinutes(int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;
        return h + "h " + m + "m";
    }

    private JobWorkSessionDTO toDTO(JobWorkSession s) {
        List<JobSessionBreakDTO> breaks = breakRepository.findByJobWorkSessionIdOrderByBreakStartTimeAsc(s.getId())
                .stream().map(this::toBreakDTO).collect(Collectors.toList());
        boolean onBreak = breaks.stream().anyMatch(b -> b.getBreakEndTime() == null);

        return JobWorkSessionDTO.builder()
                .id(s.getId())
                .employeeId(s.getEmployee().getId())
                .client(toValueDTO(s.getClient()))
                .job(toValueDTO(s.getJob()))
                .jobType(toValueDTO(s.getJobType()))
                .periodEnd(toValueDTO(s.getPeriodEnd()))
                .workDate(s.getWorkDate())
                .description(s.getDescription())
                .loginTime(s.getLoginTime())
                .logoutTime(s.getLogoutTime())
                .sessionMinutes(s.getSessionMinutes())
                .status(s.getStatus().name())
                .breaks(breaks)
                .onBreak(onBreak)
                .build();
    }

    private JobSessionBreakDTO toBreakDTO(JobSessionBreak b) {
        return JobSessionBreakDTO.builder()
                .id(b.getId())
                .breakStartTime(b.getBreakStartTime())
                .breakEndTime(b.getBreakEndTime())
                .breakMinutes(b.getBreakMinutes())
                .build();
    }

    private TimesheetMasterValueDTO toValueDTO(TimesheetMasterValue v) {
        if (v == null) return null;
        return TimesheetMasterValueDTO.builder()
                .id(v.getId())
                .type(v.getType().name())
                .value(v.getValue())
                .periodEndDate(v.getPeriodEndDate())
                .active(v.isActive())
                .build();
    }
}
