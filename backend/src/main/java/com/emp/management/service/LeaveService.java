package com.emp.management.service;

import com.emp.management.dto.LeaveDTO;
import com.emp.management.entity.EmployeeDetails;
import com.emp.management.entity.Leave;
import com.emp.management.entity.LeaveStatus;
import com.emp.management.entity.TimesheetEntry;
import com.emp.management.entity.User;
import com.emp.management.entity.Role;
import com.emp.management.exception.BadRequestException;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.EmployeeDetailsRepository;
import com.emp.management.repository.LeaveRepository;
import com.emp.management.repository.TimesheetEntryRepository;
import com.emp.management.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.Arrays;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveRepository leaveRepository;
    private final EmployeeDetailsRepository employeeDetailsRepository;
    private final TimesheetEntryRepository timesheetEntryRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final HolidayService holidayService;

    @Transactional
    public LeaveDTO applyLeave(Long employeeId, LeaveDTO dto, String callerEmail) {
        requireSelfOrPrivileged(employeeId, callerEmail);
        EmployeeDetails employee = findEmployee(employeeId);

        if (dto.getStartDate().isBefore(LocalDate.now())) {
            throw new BadRequestException("Start date cannot be in the past");
        }
        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new BadRequestException("End date cannot be before start date");
        }
        long calendarDays = dto.getEndDate().toEpochDay() - dto.getStartDate().toEpochDay() + 1;
        if (calendarDays > 30) {
            throw new BadRequestException("Leave duration cannot exceed 30 calendar days");
        }

        boolean overlap = leaveRepository.existsOverlappingLeave(
                employeeId,
                dto.getStartDate(),
                dto.getEndDate(),
                Arrays.asList(LeaveStatus.PENDING, LeaveStatus.APPROVED)
        );
        if (overlap) {
            throw new BadRequestException(
                "You already have a leave application covering one or more of the selected dates. " +
                "Please check your existing leaves before applying."
            );
        }

        // Count working days (Mon–Fri) minus applicable holidays for the employee's location + department
        String empLocation   = employee.getSeatingLocation();
        String empDepartment = employee.getDepartment();
        int totalDays = holidayService.countDeductibleDays(dto.getStartDate(), dto.getEndDate(), empLocation, empDepartment);

        if (totalDays == 0) {
            throw new BadRequestException(
                "The selected date range falls entirely on weekends or public holidays. " +
                "No working days to deduct."
            );
        }

        // Directors (ADMIN/DIRECTOR role) are auto-approved — no manual review required
        boolean isDirector = employee.getUser() != null
                && (Role.ADMIN == employee.getUser().getRole()
                    || Role.DIRECTOR == employee.getUser().getRole());

        Leave leave = Leave.builder()
                .employee(employee)
                .leaveType(dto.getLeaveType())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .totalDays(totalDays)
                .reason(dto.getReason())
                .status(isDirector ? LeaveStatus.APPROVED : LeaveStatus.PENDING)
                .approvedBy(isDirector ? employee : null)
                .managerComment(isDirector ? "Auto-approved" : null)
                .actionDate(isDirector ? LocalDateTime.now() : null)
                .build();

        Leave saved = leaveRepository.save(leave);

        createTimesheetLeaveEntries(employee, dto.getStartDate(), dto.getEndDate(), dto.getLeaveType());

        if (!isDirector) {
            // Notify manager (To) + HR and Admin (CC) — email failure must not roll back the saved leave
            try {
                String managerEmail = resolveManagerEmail(employee);
                if (managerEmail != null) {
                    String[] cc = collectHrAdminEmails(managerEmail);
                    emailService.sendLeaveRequestEmail(
                        managerEmail, cc,
                        employee.getUser().getEmail(),
                        employee.getFullName(),
                        dto.getLeaveType(),
                        dto.getStartDate(),
                        dto.getEndDate(),
                        totalDays,
                        dto.getReason()
                    );
                }
            } catch (Exception e) {
                log.error("Leave request email failed for employee {} — leave {} is saved: {}",
                        employee.getId(), saved.getId(), e.getMessage());
            }
        }

        return toDTO(saved);
    }

    @Transactional
    public LeaveDTO updateLeaveStatus(Long leaveId, String managerEmail, LeaveStatus status, String comment) {
        Leave leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave", leaveId));

        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new BadRequestException("Leave request already processed");
        }

        EmployeeDetails manager = employeeDetailsRepository.findByUserEmail(managerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found for: " + managerEmail));
        leave.setStatus(status);
        leave.setApprovedBy(manager);
        leave.setManagerComment(comment);
        leave.setActionDate(LocalDateTime.now());

        // Remove auto-created timesheet entries if rejected
        if (status == LeaveStatus.REJECTED) {
            timesheetEntryRepository.deleteByEmployeeIdAndProjectNameAndDateBetween(
                leave.getEmployee().getId(), "LEAVE",
                leave.getStartDate(), leave.getEndDate()
            );
        }

        Leave saved = leaveRepository.save(leave);

        // Notify employee (To) of the decision; CC HR and Admin — email failure must not roll back the status change
        try {
            String employeeEmail = leave.getEmployee().getUser().getEmail();
            String[] cc = collectHrAdminEmails(employeeEmail);
            emailService.sendLeaveDecisionEmail(
                employeeEmail, cc,
                leave.getEmployee().getFullName(),
                status.name(),
                leave.getLeaveType(),
                leave.getStartDate(),
                leave.getEndDate(),
                manager.getFullName(),
                comment
            );
        } catch (Exception e) {
            log.error("Leave decision email failed for leave {} — status {} is saved: {}",
                    leaveId, status, e.getMessage());
        }

        return toDTO(saved);
    }

    public List<LeaveDTO> getMyLeaves(Long employeeId, String callerEmail) {
        requireSelfOrPrivileged(employeeId, callerEmail);
        return leaveRepository.findByEmployeeIdOrderByAppliedAtDesc(employeeId).stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    public List<LeaveDTO> getPendingLeavesForManager(Long managerId) {
        return leaveRepository.findByManagerIdAndStatus(managerId, LeaveStatus.PENDING).stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    public List<LeaveDTO> getAllPendingLeaves() {
        return leaveRepository.findByStatus(LeaveStatus.PENDING).stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    public List<LeaveDTO> getAllLeaves() {
        return leaveRepository.findAllOrderByPendingFirst().stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    public List<LeaveDTO> getLeavesForManager(Long managerId) {
        return leaveRepository.findByManagerId(managerId).stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional
    public LeaveDTO updateLeave(Long leaveId, Long employeeId, LeaveDTO dto, String callerEmail) {
        Leave leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave", leaveId));

        if (!leave.getEmployee().getId().equals(employeeId)) {
            throw new BadRequestException("Leave does not belong to this employee");
        }
        requireSelfOrPrivileged(employeeId, callerEmail);

        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new BadRequestException("Only pending leaves can be edited");
        }

        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new BadRequestException("End date cannot be before start date");
        }

        boolean overlap = leaveRepository.existsOverlappingLeaveExcluding(
                employeeId,
                dto.getStartDate(),
                dto.getEndDate(),
                Arrays.asList(LeaveStatus.PENDING, LeaveStatus.APPROVED),
                leaveId
        );
        if (overlap) {
            throw new BadRequestException(
                "Updated dates overlap with an existing leave. Please choose different dates."
            );
        }

        // Remove old auto-created timesheet entries and recreate for new dates
        timesheetEntryRepository.deleteByEmployeeIdAndProjectNameAndDateBetween(
            employeeId, "LEAVE", leave.getStartDate(), leave.getEndDate()
        );

        String updLoc  = leave.getEmployee().getSeatingLocation();
        String updDept = leave.getEmployee().getDepartment();
        int totalDays = holidayService.countDeductibleDays(dto.getStartDate(), dto.getEndDate(), updLoc, updDept);

        leave.setLeaveType(dto.getLeaveType());
        leave.setStartDate(dto.getStartDate());
        leave.setEndDate(dto.getEndDate());
        leave.setTotalDays(totalDays);
        leave.setReason(dto.getReason());

        Leave saved = leaveRepository.save(leave);

        EmployeeDetails employee = findEmployee(employeeId);
        createTimesheetLeaveEntries(employee, dto.getStartDate(), dto.getEndDate(), dto.getLeaveType());

        return toDTO(saved);
    }

    @Transactional
    public void deleteLeave(Long leaveId, String callerEmail) {
        Leave leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave", leaveId));

        requireSelfOrPrivileged(leave.getEmployee().getId(), callerEmail);

        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new BadRequestException("Only pending leaves can be deleted");
        }

        // Clean up auto-created timesheet entries
        timesheetEntryRepository.deleteByEmployeeIdAndProjectNameAndDateBetween(
            leave.getEmployee().getId(), "LEAVE",
            leave.getStartDate(), leave.getEndDate()
        );

        leaveRepository.delete(leave);
    }

    private void createTimesheetLeaveEntries(EmployeeDetails employee, LocalDate start, LocalDate end, String leaveType) {
        Set<LocalDate> holidays = holidayService.getApplicableHolidayDates(start, end, employee.getSeatingLocation(), employee.getDepartment());
        LocalDate current = start;
        while (!current.isAfter(end)) {
            DayOfWeek dow = current.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY && !holidays.contains(current)) {
                if (!timesheetEntryRepository.existsByEmployeeIdAndProjectNameAndDate(
                        employee.getId(), "LEAVE", current)) {
                    timesheetEntryRepository.save(TimesheetEntry.builder()
                            .employee(employee)
                            .date(current)
                            .projectName("LEAVE")
                            .taskName(leaveType != null ? leaveType : "Leave")
                            .hours(8.0)
                            .build());
                }
            }
            current = current.plusDays(1);
        }
    }

    private String[] collectHrAdminEmails(String excludeEmail) {
        return Stream.concat(
                userRepository.findByRole(Role.HR).stream(),
                userRepository.findByRoleIn(java.util.List.of(Role.ADMIN, Role.DIRECTOR)).stream()
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
        // Fall back to first admin
        return userRepository.findByRoleIn(java.util.List.of(Role.ADMIN, Role.DIRECTOR)).stream()
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

    private EmployeeDetails findEmployee(Long id) {
        return employeeDetailsRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", id));
    }

    private LeaveDTO toDTO(Leave l) {
        EmployeeDetails emp = l.getEmployee();
        EmployeeDetails mgr = emp.getManager();
        return LeaveDTO.builder()
                .id(l.getId())
                .employeeId(emp.getId())
                .employeeName(emp.getFullName())
                .department(emp.getDepartment())
                .managerName(mgr != null ? mgr.getFullName() : null)
                .location(emp.getSeatingLocation())
                .leaveType(l.getLeaveType())
                .startDate(l.getStartDate())
                .endDate(l.getEndDate())
                .totalDays(l.getTotalDays())
                .reason(l.getReason())
                .status(l.getStatus())
                .managerComment(l.getManagerComment())
                .approvedByName(l.getApprovedBy() != null ? l.getApprovedBy().getFullName() : null)
                .appliedAt(l.getAppliedAt())
                .build();
    }
}
