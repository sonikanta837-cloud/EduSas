package com.emp.management.controller;

import com.emp.management.dto.*;
import com.emp.management.service.PipService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pip")
@RequiredArgsConstructor
public class PipController {

    private final PipService pipService;

    private String currentEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }

    // ── list ─────────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'ASSISTANT_MANAGER', 'HR', 'EMPLOYEE')")
    public ResponseEntity<List<PipDTO>> getPips() {
        return ResponseEntity.ok(pipService.getPips(currentEmail()));
    }

    @GetMapping("/employee/{empId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'ASSISTANT_MANAGER', 'HR', 'EMPLOYEE')")
    public ResponseEntity<List<PipDTO>> getPipsByEmployee(@PathVariable Long empId) {
        return ResponseEntity.ok(pipService.getPipsByEmployee(empId, currentEmail()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'ASSISTANT_MANAGER', 'HR', 'EMPLOYEE')")
    public ResponseEntity<PipDTO> getPipDetail(@PathVariable Long id) {
        return ResponseEntity.ok(pipService.getPipDetail(id, currentEmail()));
    }

    // ── create / update / delete ─────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'ASSISTANT_MANAGER', 'HR')")
    public ResponseEntity<PipDTO> createPip(@RequestBody PipDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pipService.createPip(dto, currentEmail()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'ASSISTANT_MANAGER', 'HR')")
    public ResponseEntity<PipDTO> updatePip(@PathVariable Long id, @RequestBody PipDTO dto) {
        return ResponseEntity.ok(pipService.updatePip(id, dto, currentEmail()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deletePip(@PathVariable Long id) {
        pipService.deletePip(id, currentEmail());
        return ResponseEntity.noContent().build();
    }

    // ── goals ─────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/goals")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'ASSISTANT_MANAGER', 'HR')")
    public ResponseEntity<PipGoalDTO> addGoal(@PathVariable Long id, @RequestBody PipGoalDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pipService.addGoal(id, dto, currentEmail()));
    }

    @PutMapping("/{id}/goals/{goalId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'ASSISTANT_MANAGER', 'HR')")
    public ResponseEntity<PipGoalDTO> updateGoal(@PathVariable Long id,
                                                  @PathVariable Long goalId,
                                                  @RequestBody PipGoalDTO dto) {
        return ResponseEntity.ok(pipService.updateGoal(id, goalId, dto, currentEmail()));
    }

    @DeleteMapping("/{id}/goals/{goalId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'ASSISTANT_MANAGER', 'HR')")
    public ResponseEntity<Void> deleteGoal(@PathVariable Long id, @PathVariable Long goalId) {
        pipService.deleteGoal(id, goalId, currentEmail());
        return ResponseEntity.noContent().build();
    }

    // ── weekly reviews ────────────────────────────────────────────────────────

    @PostMapping("/{id}/reviews")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'ASSISTANT_MANAGER', 'HR')")
    public ResponseEntity<PipWeeklyReviewDTO> addReview(@PathVariable Long id,
                                                         @RequestBody PipWeeklyReviewDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pipService.addWeeklyReview(id, dto, currentEmail()));
    }

    // ── comments ─────────────────────────────────────────────────────────────

    @PostMapping("/{id}/comments")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'ASSISTANT_MANAGER', 'HR', 'EMPLOYEE')")
    public ResponseEntity<PipCommentDTO> addComment(@PathVariable Long id,
                                                     @RequestBody java.util.Map<String, String> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pipService.addComment(id, body.get("content"), currentEmail()));
    }

    // ── outcome ───────────────────────────────────────────────────────────────

    @PutMapping("/{id}/outcome")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'ASSISTANT_MANAGER', 'HR')")
    public ResponseEntity<PipDTO> setOutcome(@PathVariable Long id, @RequestBody PipOutcomeDTO dto) {
        return ResponseEntity.ok(pipService.setOutcome(id, dto, currentEmail()));
    }
}
