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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveRepository leaveRepository;
    private final EmployeeDetailsRepository employeeDetailsRepository;
    private final TimesheetEntryRepository timesheetEntryRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Transactional
    public LeaveDTO applyLeave(Long employeeId, LeaveDTO dto) {
        EmployeeDetails employee = findEmployee(employeeId);

        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new BadRequestException("End date cannot be before start date");
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

        int totalDays = (int) ChronoUnit.DAYS.between(dto.getStartDate(), dto.getEndDate()) + 1;

        // Directors (ADMIN role) are auto-approved — no manual review required
        boolean isDirector = employee.getUser() != null
                && Role.ADMIN == employee.getUser().getRole();

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
            // Notify manager (To) + HR and Admin (CC)
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
        }

        return toDTO(saved);
    }

    @Transactional
    public LeaveDTO updateLeaveStatus(Long leaveId, Long managerId, LeaveStatus status, String comment) {
        Leave leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave", leaveId));

        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new BadRequestException("Leave request already processed");
        }

        EmployeeDetails manager = findEmployee(managerId);
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

        // Notify employee (To) of the decision; CC HR and Admin
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

        return toDTO(saved);
    }

    public List<LeaveDTO> getMyLeaves(Long employeeId) {
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
    public LeaveDTO updateLeave(Long leaveId, Long employeeId, LeaveDTO dto) {
        Leave leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave", leaveId));

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

        int totalDays = (int) ChronoUnit.DAYS.between(dto.getStartDate(), dto.getEndDate()) + 1;

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
    public void deleteLeave(Long leaveId) {
        Leave leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave", leaveId));

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
        LocalDate current = start;
        while (!current.isAfter(end)) {
            DayOfWeek dow = current.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
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
                userRepository.findByRole(Role.ADMIN).stream()
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
        return userRepository.findByRole(Role.ADMIN).stream()
                .map(User::getEmail)
                .findFirst()
                .orElse(null);
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
