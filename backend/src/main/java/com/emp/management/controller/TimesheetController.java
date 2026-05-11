package com.emp.management.controller;

import com.emp.management.dto.AttendanceSessionDTO;
import com.emp.management.dto.TimesheetDTO;
import com.emp.management.dto.TimesheetEntryDTO;
import com.emp.management.service.TimesheetEntryService;
import com.emp.management.service.TimesheetService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/timesheets")
@RequiredArgsConstructor
public class TimesheetController {

    private final TimesheetService timesheetService;
    private final TimesheetEntryService timesheetEntryService;

    // ── Manual check-in / check-out ──────────────────────────────────────────

    @PostMapping("/check-in/{employeeId}")
    public ResponseEntity<Void> checkIn(@PathVariable Long employeeId) {
        timesheetService.checkIn(employeeId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/check-out/{employeeId}")
    public ResponseEntity<Void> checkOut(@PathVariable Long employeeId) {
        timesheetService.checkOut(employeeId);
        return ResponseEntity.ok().build();
    }

    // ── Attendance sessions (auto-recorded on app login/logout) ──────────────

    @GetMapping("/sessions/today/{employeeId}")
    public ResponseEntity<List<AttendanceSessionDTO>> getTodaySessions(@PathVariable Long employeeId) {
        return ResponseEntity.ok(timesheetService.getTodaySessions(employeeId));
    }

    @GetMapping("/sessions/{employeeId}/range")
    public ResponseEntity<List<AttendanceSessionDTO>> getSessionsByRange(
            @PathVariable Long employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ResponseEntity.ok(timesheetService.getSessionsByRange(employeeId, start, end));
    }

    @GetMapping("/today/{employeeId}")
    public ResponseEntity<TimesheetDTO> getTodayTimesheet(@PathVariable Long employeeId) {
        return ResponseEntity.ok(timesheetService.getTodayTimesheet(employeeId));
    }

    @GetMapping("/attendance/{employeeId}")
    public ResponseEntity<List<TimesheetDTO>> getAttendance(@PathVariable Long employeeId) {
        return ResponseEntity.ok(timesheetService.getMyTimesheets(employeeId));
    }

    @GetMapping("/attendance/{employeeId}/range")
    public ResponseEntity<List<TimesheetDTO>> getAttendanceByRange(
            @PathVariable Long employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ResponseEntity.ok(timesheetService.getTimesheetsByDateRange(employeeId, start, end));
    }

    @GetMapping("/date/{date}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<TimesheetDTO>> getByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(timesheetService.getTimesheetsByDate(date));
    }

    // ── Timesheet Entries (project-based hours) ────────────────────────────

    @GetMapping("/entries/employee/{empId}")
    public ResponseEntity<List<TimesheetEntryDTO>> getEntries(
            @PathVariable Long empId,
            @RequestParam int year,
            @RequestParam int month) {
        return ResponseEntity.ok(timesheetEntryService.getMonthlyEntries(empId, year, month));
    }

    @PostMapping("/entries")
    public ResponseEntity<TimesheetEntryDTO> saveEntry(@RequestBody TimesheetEntryDTO dto) {
        return ResponseEntity.ok(timesheetEntryService.saveEntry(dto));
    }

    @DeleteMapping("/entries/{id}")
    public ResponseEntity<Void> deleteEntry(@PathVariable Long id) {
        timesheetEntryService.deleteEntry(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/entries/project")
    public ResponseEntity<Void> deleteProjectRows(
            @RequestParam Long empId,
            @RequestParam String projectName,
            @RequestParam String taskName) {
        timesheetEntryService.deleteProjectRows(empId, projectName, taskName);
        return ResponseEntity.noContent().build();
    }
}
