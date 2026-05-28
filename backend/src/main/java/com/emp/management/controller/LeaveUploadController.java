package com.emp.management.controller;

import com.emp.management.dto.LeaveUploadResult;
import com.emp.management.repository.LeaveRepository;
import com.emp.management.service.LeaveUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/leave-upload")
@RequiredArgsConstructor
public class LeaveUploadController {

    private final LeaveUploadService leaveUploadService;
    private final LeaveRepository    leaveRepository;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LeaveUploadResult> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "location", required = false) String location) {
        if (file.isEmpty()) return ResponseEntity.badRequest().build();
        try {
            return ResponseEntity.ok(leaveUploadService.processUpload(file, location));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/holiday")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LeaveUploadResult> addHoliday(@RequestBody Map<String, String> body) {
        String name     = body.get("name");
        String dateStr  = body.get("date");
        String location = body.get("location"); // optional — null means all locations
        if (name == null || name.isBlank() || dateStr == null || dateStr.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            LocalDate date = LocalDate.parse(dateStr);
            return ResponseEntity.ok(leaveUploadService.addSingleHoliday(name.trim(), date, location));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/holidays")
    public ResponseEntity<List<Map<String, Object>>> getUploadedHolidays() {
        List<Map<String, Object>> result = leaveRepository.findDistinctPublicHolidays()
                .stream()
                .map(row -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name",          row[0]);
                    m.put("date",          row[1].toString());
                    m.put("employeeCount", row[2]);
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/holidays/employees")
    public ResponseEntity<List<Map<String, Object>>> getEmployeesForHoliday(
            @RequestParam String name,
            @RequestParam String date) {
        try {
            LocalDate localDate = LocalDate.parse(date);
            List<Map<String, Object>> result = leaveRepository.findEmployeesForHoliday(localDate, name)
                    .stream()
                    .map(row -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("firstName",       row[0]);
                        m.put("lastName",        row[1]);
                        m.put("employeeCode",    row[2]);
                        m.put("location",        row[3] != null ? row[3] : "");
                        return m;
                    })
                    .collect(Collectors.toList());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/template")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> downloadTemplate() {
        try {
            byte[] bytes = leaveUploadService.generateTemplate();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"leave_upload_template.xlsx\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(bytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
