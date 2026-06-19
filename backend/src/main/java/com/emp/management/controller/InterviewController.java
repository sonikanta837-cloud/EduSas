package com.emp.management.controller;

import com.emp.management.dto.InterviewAuditLogDTO;
import com.emp.management.dto.InterviewCandidateDTO;
import com.emp.management.dto.InterviewFeedbackDTO;
import com.emp.management.dto.InterviewRoundDTO;
import com.emp.management.service.InterviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    // ── Candidates ────────────────────────────────────────────────────────────

    @PostMapping("/candidates")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR')")
    public ResponseEntity<InterviewCandidateDTO> createCandidate(
            @RequestBody InterviewCandidateDTO dto, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(interviewService.createCandidate(dto, auth.getName()));
    }

    @GetMapping("/candidates")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'MANAGER', 'ASSISTANT_MANAGER', 'HR')")
    public ResponseEntity<List<InterviewCandidateDTO>> getAllCandidates() {
        return ResponseEntity.ok(interviewService.getAllCandidates());
    }

    @GetMapping("/candidates/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'MANAGER', 'ASSISTANT_MANAGER', 'HR')")
    public ResponseEntity<InterviewCandidateDTO> getCandidate(@PathVariable Long id) {
        return ResponseEntity.ok(interviewService.getCandidate(id));
    }

    @PutMapping("/candidates/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR')")
    public ResponseEntity<InterviewCandidateDTO> updateCandidate(
            @PathVariable Long id, @RequestBody InterviewCandidateDTO dto, Authentication auth) {
        return ResponseEntity.ok(interviewService.updateCandidate(id, dto, auth.getName()));
    }

    @DeleteMapping("/candidates/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public ResponseEntity<Void> deleteCandidate(@PathVariable Long id, Authentication auth) {
        interviewService.deleteCandidate(id, auth.getName());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/candidates/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'MANAGER', 'ASSISTANT_MANAGER', 'HR')")
    public ResponseEntity<InterviewCandidateDTO> updateCandidateStatus(
            @PathVariable Long id, @RequestBody Map<String, String> body, Authentication auth) {
        return ResponseEntity.ok(interviewService.updateCandidateStatus(id, body.get("status"), auth.getName()));
    }

    // ── Rounds ────────────────────────────────────────────────────────────────

    @PostMapping("/candidates/{candidateId}/rounds")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR')")
    public ResponseEntity<InterviewRoundDTO> scheduleRound(
            @PathVariable Long candidateId,
            @RequestBody InterviewRoundDTO dto,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(interviewService.scheduleRound(candidateId, dto, auth.getName()));
    }

    @GetMapping("/candidates/{candidateId}/rounds")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'MANAGER', 'ASSISTANT_MANAGER', 'HR')")
    public ResponseEntity<List<InterviewRoundDTO>> getRoundsForCandidate(@PathVariable Long candidateId) {
        return ResponseEntity.ok(interviewService.getRoundsForCandidate(candidateId));
    }

    @GetMapping("/my-rounds")
    public ResponseEntity<List<InterviewRoundDTO>> getMyRounds(Authentication auth) {
        return ResponseEntity.ok(interviewService.getMyRounds(auth.getName()));
    }

    @PutMapping("/rounds/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR')")
    public ResponseEntity<InterviewRoundDTO> updateRound(
            @PathVariable Long id, @RequestBody InterviewRoundDTO dto, Authentication auth) {
        return ResponseEntity.ok(interviewService.updateRound(id, dto, auth.getName()));
    }

    @GetMapping("/rounds/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InterviewRoundDTO> getRound(@PathVariable Long id) {
        return ResponseEntity.ok(interviewService.getRound(id));
    }

    @PutMapping("/rounds/{id}/start")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InterviewRoundDTO> startRound(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(interviewService.startRound(id, auth.getName()));
    }

    @DeleteMapping("/rounds/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR')")
    public ResponseEntity<Void> cancelRound(@PathVariable Long id, Authentication auth) {
        interviewService.cancelRound(id, auth.getName());
        return ResponseEntity.noContent().build();
    }

    // ── Feedback ──────────────────────────────────────────────────────────────

    @PostMapping("/rounds/{roundId}/feedback")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InterviewFeedbackDTO> submitFeedback(
            @PathVariable Long roundId,
            @RequestBody InterviewFeedbackDTO dto,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(interviewService.submitFeedback(roundId, dto, auth.getName()));
    }

    @PutMapping("/feedback/{feedbackId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InterviewFeedbackDTO> updateFeedback(
            @PathVariable Long feedbackId,
            @RequestBody InterviewFeedbackDTO dto,
            Authentication auth) {
        return ResponseEntity.ok(interviewService.updateFeedback(feedbackId, dto, auth.getName()));
    }

    @GetMapping("/rounds/{roundId}/feedback")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'MANAGER', 'ASSISTANT_MANAGER', 'HR')")
    public ResponseEntity<List<InterviewFeedbackDTO>> getFeedbackForRound(@PathVariable Long roundId) {
        return ResponseEntity.ok(interviewService.getFeedbackForRound(roundId));
    }

    // ── Stats & Audit ─────────────────────────────────────────────────────────

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'MANAGER', 'ASSISTANT_MANAGER', 'HR')")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(interviewService.getStats());
    }

    @GetMapping("/audit-logs")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public ResponseEntity<List<InterviewAuditLogDTO>> getAuditLogs() {
        return ResponseEntity.ok(interviewService.getAuditLogs());
    }

    @GetMapping("/candidates/{id}/audit-logs")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'MANAGER', 'ASSISTANT_MANAGER', 'HR')")
    public ResponseEntity<List<InterviewAuditLogDTO>> getAuditLogsForCandidate(@PathVariable Long id) {
        return ResponseEntity.ok(interviewService.getAuditLogsForCandidate(id));
    }
}
