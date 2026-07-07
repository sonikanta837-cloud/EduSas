package com.emp.management.controller;

import com.emp.management.dto.InterviewQuestionDTO;
import com.emp.management.service.InterviewQuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interview-questions")
@RequiredArgsConstructor
public class InterviewQuestionController {

    private final InterviewQuestionService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR', 'MANAGER', 'ASSISTANT_MANAGER')")
    public ResponseEntity<List<InterviewQuestionDTO>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR', 'MANAGER', 'ASSISTANT_MANAGER')")
    public ResponseEntity<InterviewQuestionDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR', 'MANAGER', 'ASSISTANT_MANAGER')")
    public ResponseEntity<InterviewQuestionDTO> create(
            @RequestBody InterviewQuestionDTO dto, Authentication auth) {
        return ResponseEntity.ok(service.create(dto, auth.getName()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR', 'MANAGER', 'ASSISTANT_MANAGER')")
    public ResponseEntity<InterviewQuestionDTO> update(
            @PathVariable Long id, @RequestBody InterviewQuestionDTO dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR', 'MANAGER', 'ASSISTANT_MANAGER')")
    public ResponseEntity<InterviewQuestionDTO> toggleStatus(@PathVariable Long id) {
        return ResponseEntity.ok(service.toggleStatus(id));
    }

    @GetMapping("/technologies")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR', 'MANAGER', 'ASSISTANT_MANAGER')")
    public ResponseEntity<List<String>> getTechnologies() {
        return ResponseEntity.ok(service.getTechnologies());
    }

    @GetMapping("/categories")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR', 'MANAGER', 'ASSISTANT_MANAGER')")
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(service.getCategories());
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR', 'MANAGER', 'ASSISTANT_MANAGER')")
    public ResponseEntity<Map<String, Long>> getStats() {
        return ResponseEntity.ok(service.getStats());
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR', 'MANAGER', 'ASSISTANT_MANAGER')")
    public ResponseEntity<Map<String, Object>> importExcel(
            @RequestParam("file") MultipartFile file, Authentication auth) {
        return ResponseEntity.ok(service.importFromExcel(file, auth.getName()));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR', 'MANAGER', 'ASSISTANT_MANAGER')")
    public ResponseEntity<byte[]> exportExcel() throws IOException {
        byte[] data = service.exportToExcel();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"interview-questions.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }
}
