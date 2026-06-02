package com.emp.management.service;

import com.emp.management.dto.TimesheetEntryDTO;
import com.emp.management.dto.WorkReportDTO;
import com.emp.management.entity.EmployeeDetails;
import com.emp.management.entity.TimesheetEntry;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.EmployeeDetailsRepository;
import com.emp.management.repository.TimesheetEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TimesheetEntryService {

    private final TimesheetEntryRepository repository;
    private final EmployeeDetailsRepository employeeDetailsRepository;

    @Transactional(readOnly = true)
    public List<WorkReportDTO> getWorkReport(LocalDate start, LocalDate end) {
        return repository.findAllForWorkReport(start, end).stream().map(e -> {
            var emp = e.getEmployee();
            String mgr = (emp.getManager() != null) ? emp.getManager().getFullName() : "—";
            return WorkReportDTO.builder()
                    .entryId(e.getId())
                    .employeeId(emp.getId())
                    .employeeCode(emp.getEmployeeCode() != null ? emp.getEmployeeCode() : "—")
                    .employeeName(emp.getFullName())
                    .department(emp.getDepartment() != null ? emp.getDepartment() : "—")
                    .location(emp.getSeatingLocation() != null ? emp.getSeatingLocation() : "—")
                    .managerName(mgr)
                    .date(e.getDate())
                    .projectName(e.getProjectName())
                    .taskName(e.getTaskName() != null ? e.getTaskName() : "—")
                    .hours(e.getHours())
                    .build();
        }).toList();
    }

    public List<TimesheetEntryDTO> getMonthlyEntries(Long empId, int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        return repository.findByEmployeeIdAndDateBetweenOrderByDateAsc(empId, start, end)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public void verifyAccess(Long targetEmpId, String callerEmail) {
        EmployeeDetails caller = employeeDetailsRepository.findByUserEmail(callerEmail).orElse(null);
        if (caller == null) return;
        String role = caller.getUser().getRole().name();
        if ("ADMIN".equals(role)) return;
        if ("MANAGER".equals(role) || "ASSISTANT_MANAGER".equals(role)) {
            if (caller.getId().equals(targetEmpId)) return;
            EmployeeDetails target = employeeDetailsRepository.findById(targetEmpId).orElse(null);
            if (target != null && target.getManager() != null
                    && target.getManager().getId().equals(caller.getId())) return;
            throw new AccessDeniedException("You can only edit timesheets for your direct reports");
        }
        if (!caller.getId().equals(targetEmpId)) {
            throw new AccessDeniedException("You can only edit your own timesheet");
        }
    }

    @Transactional
    public TimesheetEntryDTO saveEntry(TimesheetEntryDTO dto, String callerEmail) {
        verifyAccess(dto.getEmployeeId(), callerEmail);
        EmployeeDetails employee = employeeDetailsRepository.findById(dto.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee", dto.getEmployeeId()));

        TimesheetEntry entry;
        if (dto.getId() != null) {
            entry = repository.findById(dto.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("TimesheetEntry", dto.getId()));
        } else {
            entry = TimesheetEntry.builder().employee(employee).build();
        }

        entry.setDate(dto.getDate());
        entry.setProjectName(dto.getProjectName());
        entry.setTaskName(dto.getTaskName() != null ? dto.getTaskName() : "");
        entry.setHours(dto.getHours());

        return toDTO(repository.save(entry));
    }

    @Transactional
    public void deleteEntry(Long id, String callerEmail) {
        TimesheetEntry entry = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TimesheetEntry", id));
        verifyAccess(entry.getEmployee().getId(), callerEmail);
        repository.deleteById(id);
    }

    @Transactional
    public void deleteProjectRows(Long empId, String projectName, String taskName, String callerEmail) {
        verifyAccess(empId, callerEmail);
        repository.deleteByEmployeeIdAndProjectNameAndTaskName(empId, projectName, taskName);
    }

    private TimesheetEntryDTO toDTO(TimesheetEntry e) {
        return TimesheetEntryDTO.builder()
                .id(e.getId())
                .employeeId(e.getEmployee().getId())
                .date(e.getDate())
                .projectName(e.getProjectName())
                .taskName(e.getTaskName())
                .hours(e.getHours())
                .build();
    }
}
