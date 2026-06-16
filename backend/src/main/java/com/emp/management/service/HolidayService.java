package com.emp.management.service;

import com.emp.management.dto.HolidayDTO;
import com.emp.management.entity.Holiday;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.HolidayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HolidayService {

    private final HolidayRepository holidayRepository;

    // ── CRUD ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<HolidayDTO> getAll(Integer year) {
        List<Holiday> list = (year != null)
                ? holidayRepository.findByYear(year)
                : holidayRepository.findAllOrderByDate();
        return list.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<HolidayDTO> getForEmployee(String location, String department, int year) {
        String loc  = (location   == null || location.isBlank())   ? "" : location;
        String dept = (department == null || department.isBlank())  ? null  : department;
        return holidayRepository.findActiveByLocationAndYear(loc, dept, year)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Integer> getDistinctYears() {
        List<Integer> years = holidayRepository.findDistinctYears();
        int current = LocalDate.now().getYear();
        if (!years.contains(current)) years.add(0, current);
        return years;
    }

    @Transactional
    public HolidayDTO create(HolidayDTO dto) {
        Holiday h = Holiday.builder()
                .name(dto.getName())
                .date(dto.getDate())
                .holidayType(dto.getHolidayType())
                .location(dto.getLocation() != null && !dto.getLocation().isBlank() ? dto.getLocation() : "")
                .department(dto.getDepartment() == null || dto.getDepartment().isBlank() ? null : dto.getDepartment())
                .description(dto.getDescription())
                .active(dto.isActive())
                .build();
        return toDTO(holidayRepository.save(h));
    }

    @Transactional
    public HolidayDTO update(Long id, HolidayDTO dto) {
        Holiday h = find(id);
        h.setName(dto.getName());
        h.setDate(dto.getDate());
        h.setHolidayType(dto.getHolidayType());
        h.setLocation(dto.getLocation() != null && !dto.getLocation().isBlank() ? dto.getLocation() : "");
        h.setDepartment(dto.getDepartment() == null || dto.getDepartment().isBlank() ? null : dto.getDepartment());
        h.setDescription(dto.getDescription());
        h.setActive(dto.isActive());
        return toDTO(holidayRepository.save(h));
    }

    @Transactional
    public void delete(Long id) {
        holidayRepository.delete(find(id));
    }

    @Transactional
    public List<HolidayDTO> bulkCreate(List<HolidayDTO> dtos) {
        return dtos.stream().map(this::create).collect(Collectors.toList());
    }

    // ── Leave integration ─────────────────────────────────────────────────────

    /**
     * Returns the set of applicable holiday dates for a given location within a date range.
     * Used by LeaveService to exclude holidays from leave deductions.
     */
    public Set<LocalDate> getApplicableHolidayDates(LocalDate start, LocalDate end, String location, String department) {
        String loc  = (location   == null || location.isBlank())   ? "ALL" : location;
        String dept = (department == null || department.isBlank())  ? null  : department;
        return holidayRepository.findApplicable(loc, dept, start, end)
                .stream().map(Holiday::getDate).collect(Collectors.toSet());
    }

    // Convenience overload used by callers that don't have department context
    public Set<LocalDate> getApplicableHolidayDates(LocalDate start, LocalDate end, String location) {
        return getApplicableHolidayDates(start, end, location, null);
    }

    public int countDeductibleDays(LocalDate start, LocalDate end, String location, String department) {
        Set<LocalDate> holidays = getApplicableHolidayDates(start, end, location, department);
        int count = 0;
        LocalDate d = start;
        while (!d.isAfter(end)) {
            DayOfWeek dow = d.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY && !holidays.contains(d)) count++;
            d = d.plusDays(1);
        }
        return count;
    }

    /**
     * Counts working days (Mon–Fri) in [start, end] excluding applicable holidays.
     * This is the number of leave days that will be deducted.
     */
    public int countDeductibleDays(LocalDate start, LocalDate end, String location) {
        Set<LocalDate> holidays = getApplicableHolidayDates(start, end, location, null);
        int count = 0;
        LocalDate d = start;
        while (!d.isAfter(end)) {
            DayOfWeek dow = d.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY && !holidays.contains(d)) {
                count++;
            }
            d = d.plusDays(1);
        }
        return count;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Holiday find(Long id) {
        return holidayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday", id));
    }

    public HolidayDTO toDTO(Holiday h) {
        return HolidayDTO.builder()
                .id(h.getId())
                .name(h.getName())
                .date(h.getDate())
                .holidayType(h.getHolidayType())
                .location(h.getLocation())
                .department(h.getDepartment())
                .description(h.getDescription())
                .active(h.isActive())
                .createdAt(h.getCreatedAt())
                .updatedAt(h.getUpdatedAt())
                .build();
    }
}
