package com.emp.management.service;

import com.emp.management.dto.TimesheetEntryDTO;
import com.emp.management.entity.EmployeeDetails;
import com.emp.management.entity.TimesheetEntry;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.EmployeeDetailsRepository;
import com.emp.management.repository.TimesheetEntryRepository;
import lombok.RequiredArgsConstructor;
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

    public List<TimesheetEntryDTO> getMonthlyEntries(Long empId, int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        return repository.findByEmployeeIdAndDateBetweenOrderByDateAsc(empId, start, end)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional
    public TimesheetEntryDTO saveEntry(TimesheetEntryDTO dto) {
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
    public void deleteEntry(Long id) {
        repository.deleteById(id);
    }

    @Transactional
    public void deleteProjectRows(Long empId, String projectName, String taskName) {
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
