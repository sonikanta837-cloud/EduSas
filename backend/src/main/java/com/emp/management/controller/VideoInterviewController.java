package com.emp.management.controller;

import com.emp.management.dto.*;
import com.emp.management.service.VideoInterviewService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/video-interview")
@RequiredArgsConstructor
public class VideoInterviewController {

    private final VideoInterviewService service;

    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC — Candidate-facing endpoints (no JWT required)
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/candidate/{token}/validate")
    public ResponseEntity<CandidateInterviewDTO> validate(@PathVariable String token) {
        return ResponseEntity.ok(service.validateToken(token));
    }

    @PostMapping("/candidate/{token}/start")
    public ResponseEntity<CandidateInterviewDTO> start(
            @PathVariable String token, HttpServletRequest req) {
        String ip = getClientIp(req);
        return ResponseEntity.ok(service.startInterview(token, ip));
    }

    @PostMapping("/candidate/{token}/save-answer")
    public ResponseEntity<Void> saveAnswer(
            @PathVariable String token, @RequestBody SaveAnswerRequest request) {
        service.saveAnswer(token, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/candidate/{token}/violation")
    public ResponseEntity<Map<String, Object>> logViolation(
            @PathVariable String token,
            @RequestBody ViolationRequest request,
            HttpServletRequest req) {
        String ip = getClientIp(req);
        return ResponseEntity.ok(service.logViolation(token, request, ip));
    }

    @PostMapping("/candidate/{token}/submit")
    public ResponseEntity<Void> submit(@PathVariable String token) {
        service.submitInterview(token);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/candidate/{token}/recording", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadRecording(
            @PathVariable String token,
            @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(service.uploadRecording(token, file));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AUTHENTICATED — HR / Manager / Admin facing endpoints
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR', 'MANAGER', 'ASSISTANT_MANAGER')")
    public ResponseEntity<List<VideoInterviewDTO>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR', 'MANAGER', 'ASSISTANT_MANAGER')")
    public ResponseEntity<VideoInterviewDTO> create(
            @RequestBody CreateVideoInterviewRequest req, Authentication auth) {
        return ResponseEntity.ok(service.create(req, auth.getName()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR', 'MANAGER', 'ASSISTANT_MANAGER')")
    public ResponseEntity<VideoInterviewDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR', 'MANAGER', 'ASSISTANT_MANAGER')")
    public ResponseEntity<Map<String, Long>> stats() {
        return ResponseEntity.ok(service.getStats());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String getClientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
