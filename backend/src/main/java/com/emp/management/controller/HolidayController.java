package com.emp.management.controller;

import com.emp.management.dto.HolidayDTO;
import com.emp.management.entity.EmployeeDetails;
import com.emp.management.repository.EmployeeDetailsRepository;
import com.emp.management.service.HolidayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/holidays")
@RequiredArgsConstructor
public class HolidayController {

    private final HolidayService holidayService;
    private final EmployeeDetailsRepository employeeDetailsRepository;

    /** Admin/HR: all holidays, optionally filtered by year */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR', 'MANAGER', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
    public ResponseEntity<List<HolidayDTO>> getAll(
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(holidayService.getAll(year));
    }

    /** Any employee: holidays applicable to their own seatingLocation */
    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR', 'MANAGER', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
    public ResponseEntity<List<HolidayDTO>> getMy(
            @RequestParam(defaultValue = "0") int year,
            Authentication authentication) {
        int y = (year == 0) ? LocalDate.now().getYear() : year;
        EmployeeDetails emp = employeeDetailsRepository
                .findByUserEmail(authentication.getName()).orElse(null);
        String location   = (emp != null && emp.getSeatingLocation() != null) ? emp.getSeatingLocation() : "ALL";
        String department = (emp != null) ? emp.getDepartment() : null;
        return ResponseEntity.ok(holidayService.getForEmployee(location, department, y));
    }

    /** Available years for the year-picker */
    @GetMapping("/years")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR', 'MANAGER', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
    public ResponseEntity<List<Integer>> getYears() {
        return ResponseEntity.ok(holidayService.getDistinctYears());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR')")
    public ResponseEntity<HolidayDTO> create(@Valid @RequestBody HolidayDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(holidayService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR')")
    public ResponseEntity<HolidayDTO> update(@PathVariable Long id,
                                              @Valid @RequestBody HolidayDTO dto) {
        return ResponseEntity.ok(holidayService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        holidayService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Bulk-create from a JSON array */
    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR')")
    public ResponseEntity<List<HolidayDTO>> bulkCreate(@Valid @RequestBody List<HolidayDTO> dtos) {
        return ResponseEntity.ok(holidayService.bulkCreate(dtos));
    }
}
