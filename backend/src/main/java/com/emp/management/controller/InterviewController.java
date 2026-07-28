package com.emp.management.controller;

import com.emp.management.dto.*;
import com.emp.management.dto.DirectorRoomDTO;
import com.emp.management.service.AtsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
public class InterviewController {

    private final AtsService atsService;

    // ── CV Bank ───────────────────────────────────────────────────────────────

    /**
     * Step 1 of the two-step upload flow.
     * Immediately saves the file to disk and returns all auto-parsed fields.
     * The client caches the returned resumePath and sends it in Step 2.
     */
    @PostMapping(value = "/candidates/store-resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR')")
    public ResponseEntity<Map<String, Object>> storeResume(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(atsService.storeAndParseResume(file));
    }

    /** Legacy single-call parse (still used for quick preview without persisting). */
    @PostMapping(value = "/candidates/parse-resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR')")
    public ResponseEntity<Map<String, String>> parseResume(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(atsService.parseResume(file));
    }

    /** Parses raw text (e.g. from browser-side OCR) and returns structured candidate fields. */
    @PostMapping("/candidates/parse-text")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR')")
    public ResponseEntity<Map<String, String>> parseText(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(atsService.parseTextFields(body.getOrDefault("text", "")));
    }

    /**
     * Step 2 of the two-step upload flow.
     * Receives form fields + the resumePath returned by /store-resume.
     * No file re-upload needed — file is already on disk.
     */
    @PostMapping(value = "/candidates", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR')")
    public ResponseEntity<AtsCandidateDTO> uploadCandidate(
            @RequestParam(value = "resumePath",           required = false) String resumePath,
            @RequestParam(value = "resumeOriginalName",   required = false) String resumeOriginalName,
            @RequestParam("name")                                           String name,
            @RequestParam(value = "email",                required = false) String email,
            @RequestParam(value = "phone",                required = false) String phone,
            @RequestParam(value = "address",              required = false) String address,
            @RequestParam(value = "addressStreet",     required = false) String addressStreet,
            @RequestParam(value = "addressArea",       required = false) String addressArea,
            @RequestParam(value = "addressLandmark",   required = false) String addressLandmark,
            @RequestParam(value = "addressCity",       required = false) String addressCity,
            @RequestParam(value = "addressDistrict",   required = false) String addressDistrict,
            @RequestParam(value = "addressState",      required = false) String addressState,
            @RequestParam(value = "addressPostalCode", required = false) String addressPostalCode,
            @RequestParam(value = "addressCountry",    required = false) String addressCountry,
            @RequestParam("appliedProfile")                                 String appliedProfile,
            @RequestParam(value = "officeLocation",       required = false) String officeLocation,
            @RequestParam(value = "source",               required = false) String source,
            @RequestParam(value = "skills",               required = false) String skills,
            @RequestParam(value = "totalExperienceYears", required = false) String totalExperienceYears,
            @RequestParam(value = "linkedinUrl",          required = false) String linkedinUrl,
            @RequestParam(value = "githubUrl",            required = false) String githubUrl,
            @RequestParam(value = "educationSummary",     required = false) String educationSummary,
            @RequestParam(value = "currentDesignation",   required = false) String currentDesignation,
            @RequestParam(value = "currentCompanyCv",     required = false) String currentCompanyCv,
            @RequestParam(value = "rawResumeText",        required = false) String rawResumeText,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(atsService.uploadCandidate(
                        resumePath, resumeOriginalName,
                        name, email, phone, address,
                        addressStreet, addressArea, addressLandmark,
                        addressCity, addressDistrict, addressState,
                        addressPostalCode, addressCountry,
                        appliedProfile, officeLocation, source,
                        skills, totalExperienceYears, linkedinUrl, githubUrl,
                        educationSummary, currentDesignation, currentCompanyCv, rawResumeText,
                        auth.getName()));
    }

    @GetMapping("/candidates")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'MANAGER', 'ASSISTANT_MANAGER', 'HR')")
    public ResponseEntity<List<AtsCandidateDTO>> getAllCandidates() {
        return ResponseEntity.ok(atsService.getAllCandidates());
    }

    @GetMapping("/candidates/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'MANAGER', 'ASSISTANT_MANAGER', 'HR')")
    public ResponseEntity<byte[]> exportCandidateReport() throws IOException {
        byte[] excel = atsService.exportCandidateReport();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"candidate-pipeline-report.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    @GetMapping("/candidates/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'MANAGER', 'ASSISTANT_MANAGER', 'HR')")
    public ResponseEntity<AtsCandidateDTO> getCandidate(@PathVariable Long id) {
        return ResponseEntity.ok(atsService.getCandidate(id));
    }

    @PutMapping("/candidates/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR')")
    public ResponseEntity<AtsCandidateDTO> updateCandidate(
            @PathVariable Long id,
            @RequestBody AtsCandidateDTO dto,
            Authentication auth) {
        return ResponseEntity.ok(atsService.updateCandidate(id, dto));
    }

    @DeleteMapping("/candidates/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public ResponseEntity<Void> deleteCandidate(@PathVariable Long id) {
        atsService.deleteCandidate(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/candidates/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR')")
    public ResponseEntity<AtsCandidateDTO> rejectFromCvBank(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(atsService.rejectFromCvBank(id, auth.getName()));
    }

    @PostMapping("/candidates/{id}/open-hr")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR')")
    public ResponseEntity<AtsCandidateDTO> openHrScreening(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(atsService.openHrScreening(id, auth.getName()));
    }

    @GetMapping("/candidates/{id}/resume")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'MANAGER', 'ASSISTANT_MANAGER', 'HR')")
    public ResponseEntity<Resource> downloadResume(@PathVariable Long id) {
        return atsService.downloadResume(id);
    }

    // ── HR Screening ──────────────────────────────────────────────────────────

    @PostMapping("/candidates/{id}/hr-screening")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR')")
    public ResponseEntity<AtsHrScreeningDTO> saveHrScreening(
            @PathVariable Long id,
            @RequestBody AtsHrScreeningDTO dto,
            Authentication auth) {
        return ResponseEntity.ok(atsService.saveHrScreening(id, dto, auth.getName()));
    }

    @PostMapping("/candidates/{id}/hr-screening/decision")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR')")
    public ResponseEntity<AtsCandidateDTO> submitHrDecision(
            @PathVariable Long id,
            @RequestBody AtsHrScreeningDTO dto,
            Authentication auth) {
        return ResponseEntity.ok(atsService.submitHrDecision(id, dto, auth.getName()));
    }

    // ── Technical Interviews ──────────────────────────────────────────────────

    @GetMapping("/my-assignments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AtsTechnicalInterviewDTO>> getMyAssignments(Authentication auth) {
        return ResponseEntity.ok(atsService.getMyTechnicalInterviews(auth.getName()));
    }

    @GetMapping("/my-rounds")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<InterviewRoundDTO>> getMyRounds(Authentication auth) {
        return ResponseEntity.ok(atsService.getMyRounds(auth.getName()));
    }

    @PostMapping("/candidates/{id}/technical")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR')")
    public ResponseEntity<AtsTechnicalInterviewDTO> assignTechnicalInterview(
            @PathVariable Long id,
            @RequestBody AtsTechnicalInterviewDTO dto,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(atsService.assignTechnicalInterview(id, dto, auth.getName()));
    }

    @PostMapping("/technical/{id}/feedback")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AtsCandidateDTO> submitTechnicalFeedback(
            @PathVariable Long id,
            @RequestBody AtsTechnicalInterviewDTO dto,
            Authentication auth) {
        return ResponseEntity.ok(atsService.submitTechnicalFeedback(id, dto, auth.getName()));
    }

    // ── Video Interview — Manager Endpoints ───────────────────────────────────

    @PostMapping("/technical/{id}/generate-link")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AtsTechnicalInterviewDTO> generateInterviewLink(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            Authentication auth) {
        String technology   = (String) body.get("technology");
        String difficulty   = (String) body.get("difficulty");
        Integer questionCount = body.get("questionCount") != null
                ? Integer.parseInt(body.get("questionCount").toString()) : null;
        return ResponseEntity.ok(atsService.generateInterviewLink(id, technology, difficulty, questionCount, auth.getName()));
    }

    @GetMapping("/technical/{id}/room")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ManagerRoomDTO> getManagerRoom(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(atsService.getManagerRoom(id));
    }

    @PostMapping("/technical/{id}/evaluate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AtsCandidateDTO> submitManagerEvaluation(
            @PathVariable Long id,
            @RequestBody AtsTechnicalInterviewDTO dto,
            Authentication auth) {
        return ResponseEntity.ok(atsService.submitManagerEvaluation(id, dto, auth.getName()));
    }

    // WebRTC signaling — manager side (authenticated)
    @PostMapping("/technical/{id}/signal/answer")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> saveAnswerSdp(@PathVariable Long id, @RequestBody Map<String, String> body) {
        atsService.saveAnswerSdp(id, body.get("sdp"));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/technical/{id}/signal/offer")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> getOffer(@PathVariable Long id) {
        String sdp = atsService.getOffer(id);
        return ResponseEntity.ok(Map.of("sdp", sdp != null ? sdp : ""));
    }

    @PostMapping("/technical/{id}/signal/ice")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> addManagerIce(@PathVariable Long id, @RequestBody Map<String, String> body) {
        atsService.addManagerIce(id, body.get("ice"));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/technical/{id}/signal/ice/candidate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<String>> getCandidateIce(@PathVariable Long id) {
        return ResponseEntity.ok(atsService.getCandidateIce(id));
    }

    // ── Video Interview — Candidate Public Endpoints (no JWT) ─────────────────

    @GetMapping("/technical/candidate/{token}")
    public ResponseEntity<CandidateRoomDTO> getCandidateRoom(@PathVariable String token) {
        return ResponseEntity.ok(atsService.getCandidateRoom(token));
    }

    @PostMapping("/technical/candidate/{token}/start")
    public ResponseEntity<Map<String, String>> startInterview(@PathVariable String token) {
        atsService.startInterview(token);
        return ResponseEntity.ok(Map.of("status", "IN_PROGRESS"));
    }

    @PostMapping("/technical/candidate/{token}/answer")
    public ResponseEntity<Void> saveAnswer(
            @PathVariable String token,
            @RequestBody Map<String, Object> body) {
        Long questionId  = Long.parseLong(body.get("questionId").toString());
        String answerText       = (String) body.get("answerText");
        String selectedOption   = (String) body.get("selectedOption");
        atsService.saveAnswer(token, questionId, answerText, selectedOption);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/technical/candidate/{token}/violation")
    public ResponseEntity<Map<String, Object>> logViolation(
            @PathVariable String token,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null) ip = request.getRemoteAddr();
        return ResponseEntity.ok(atsService.logViolation(
                token, body.get("violationType"), body.get("description"), ip));
    }

    @PostMapping("/technical/candidate/{token}/submit")
    public ResponseEntity<Map<String, Integer>> submitInterview(@PathVariable String token) {
        return ResponseEntity.ok(atsService.submitInterview(token));
    }

    @PostMapping(value = "/technical/candidate/{token}/recording", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadRecording(
            @PathVariable String token,
            @RequestParam("file") MultipartFile file) {
        atsService.uploadRecording(token, file);
        return ResponseEntity.ok().build();
    }

    // WebRTC signaling — candidate side (public)
    @PostMapping("/technical/candidate/{token}/signal/offer")
    public ResponseEntity<Void> saveOffer(@PathVariable String token, @RequestBody Map<String, String> body) {
        atsService.saveOffer(token, body.get("sdp"));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/technical/candidate/{token}/signal/answer")
    public ResponseEntity<Map<String, String>> getAnswerSdp(@PathVariable String token) {
        String sdp = atsService.getAnswerSdp(token);
        return ResponseEntity.ok(Map.of("sdp", sdp != null ? sdp : ""));
    }

    @PostMapping("/technical/candidate/{token}/signal/ice")
    public ResponseEntity<Void> addCandidateIce(@PathVariable String token, @RequestBody Map<String, String> body) {
        atsService.addCandidateIce(token, body.get("ice"));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/technical/candidate/{token}/signal/ice/manager")
    public ResponseEntity<List<String>> getManagerIce(@PathVariable String token) {
        return ResponseEntity.ok(atsService.getManagerIce(token));
    }

    @GetMapping("/directors")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR')")
    public ResponseEntity<List<EmployeeDTO>> getEligibleDirectors() {
        return ResponseEntity.ok(atsService.getEligibleDirectors());
    }

    // ── Final Round completion — Director notes, then HR decision ─────────────

    /** Director step: submits interview notes + an advisory recommendation. Only the assigned Director (or Admin) may call this. */
    @PostMapping("/candidates/{id}/final")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public ResponseEntity<AtsFinalRoundDTO> saveFinalRound(
            @PathVariable Long id,
            @RequestBody AtsFinalRoundDTO dto,
            Authentication auth) {
        return ResponseEntity.ok(atsService.saveFinalRound(id, dto, auth.getName()));
    }

    /** HR step: reviews the Director's notes and records the actual hiring decision + offer/rejection email. */
    @PostMapping("/final/{id}/decision")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<AtsCandidateDTO> submitFinalDecision(
            @PathVariable Long id,
            @RequestBody AtsFinalRoundDTO dto,
            Authentication auth) {
        return ResponseEntity.ok(atsService.submitFinalDecision(id, dto, auth.getName()));
    }

    // ── Final Round Video Interview — HR/Director Endpoints ────────────────────

    /** HR/Admin step: assigns the candidate to a Director for the Final Round. */
    @PostMapping("/candidates/{id}/final/assign-director")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<AtsFinalRoundDTO> assignFinalRoundDirector(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            Authentication auth) {
        Long directorId = body.get("directorId") != null
                ? Long.parseLong(body.get("directorId").toString()) : null;
        return ResponseEntity.ok(atsService.assignFinalRoundDirector(id, directorId, auth.getName()));
    }

    /** Director step: schedules the Final Interview and (re)generates the secure candidate link. */
    @PostMapping("/candidates/{id}/final/generate-link")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public ResponseEntity<AtsFinalRoundDTO> generateFinalInterviewLink(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            Authentication auth) {
        LocalDateTime scheduledAt = body.get("scheduledAt") != null && !body.get("scheduledAt").toString().isBlank()
                ? LocalDateTime.parse(body.get("scheduledAt").toString()) : null;
        return ResponseEntity.ok(atsService.generateFinalInterviewLink(id, scheduledAt, auth.getName()));
    }

    @GetMapping("/final/{id}/director-room")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DirectorRoomDTO> getDirectorRoom(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(atsService.getDirectorRoom(id, auth.getName()));
    }

    @PostMapping("/final/{id}/evaluate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AtsCandidateDTO> submitDirectorEvaluation(
            @PathVariable Long id,
            @RequestBody AtsFinalRoundDTO dto,
            Authentication auth) {
        return ResponseEntity.ok(atsService.submitDirectorEvaluation(id, dto, auth.getName()));
    }

    // WebRTC signaling — director side (authenticated)
    @PostMapping("/final/{id}/signal/answer")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> saveFinalAnswerSdp(@PathVariable Long id, @RequestBody Map<String, String> body) {
        atsService.saveFinalAnswerSdp(id, body.get("sdp"));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/final/{id}/signal/offer")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> getFinalOffer(@PathVariable Long id) {
        String sdp = atsService.getFinalOffer(id);
        return ResponseEntity.ok(Map.of("sdp", sdp != null ? sdp : ""));
    }

    @PostMapping("/final/{id}/signal/ice")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> addDirectorIce(@PathVariable Long id, @RequestBody Map<String, String> body) {
        atsService.addDirectorIce(id, body.get("ice"));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/final/{id}/signal/ice/candidate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<String>> getCandidateFinalIce(@PathVariable Long id) {
        return ResponseEntity.ok(atsService.getCandidateFinalIce(id));
    }

    // ── Final Round Video Interview — Candidate Public Endpoints (no JWT) ─────

    @GetMapping("/final/candidate/{token}")
    public ResponseEntity<AtsFinalRoundDTO> getCandidateFinalRoom(@PathVariable String token) {
        return ResponseEntity.ok(atsService.getCandidateFinalRoom(token));
    }

    @PostMapping("/final/candidate/{token}/start")
    public ResponseEntity<Map<String, String>> startFinalInterview(@PathVariable String token) {
        atsService.startFinalInterview(token);
        return ResponseEntity.ok(Map.of("status", "IN_PROGRESS"));
    }

    @PostMapping("/final/candidate/{token}/submit")
    public ResponseEntity<Map<String, String>> submitFinalInterview(@PathVariable String token) {
        atsService.submitFinalInterview(token);
        return ResponseEntity.ok(Map.of("status", "CANDIDATE_SUBMITTED"));
    }

    @PostMapping(value = "/final/candidate/{token}/recording", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadFinalRecording(
            @PathVariable String token,
            @RequestParam("file") MultipartFile file) {
        atsService.uploadFinalRecording(token, file);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/final/candidate/{token}/signal/offer")
    public ResponseEntity<Void> saveFinalOffer(@PathVariable String token, @RequestBody Map<String, String> body) {
        atsService.saveFinalOffer(token, body.get("sdp"));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/final/candidate/{token}/signal/answer")
    public ResponseEntity<Map<String, String>> getFinalAnswerSdp(@PathVariable String token) {
        String sdp = atsService.getFinalAnswerSdp(token);
        return ResponseEntity.ok(Map.of("sdp", sdp != null ? sdp : ""));
    }

    @PostMapping("/final/candidate/{token}/signal/ice")
    public ResponseEntity<Void> addCandidateFinalIce(@PathVariable String token, @RequestBody Map<String, String> body) {
        atsService.addCandidateFinalIce(token, body.get("ice"));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/final/candidate/{token}/signal/ice/director")
    public ResponseEntity<List<String>> getDirectorIce(@PathVariable String token) {
        return ResponseEntity.ok(atsService.getDirectorIce(token));
    }

    // ── Duplicate Detection ───────────────────────────────────────────────────

    /**
     * Checks if a candidate already exists in the CV Bank by email → phone → name priority.
     * Returns 200 + candidate data when a duplicate is found, or 204 No Content when clear.
     */
    @PostMapping("/candidates/check-duplicate")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR')")
    public ResponseEntity<AtsCandidateDTO> checkDuplicate(@RequestBody Map<String, String> body) {
        AtsCandidateDTO dup = atsService.checkDuplicate(
                body.get("email"), body.get("phone"), body.get("name"));
        if (dup == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(dup);
    }

    // ── Replace Resume ────────────────────────────────────────────────────────

    /**
     * Archives the existing CV and stores the new one as the active version.
     * Accepts the same form fields as the /candidates create endpoint.
     */
    @PostMapping(value = "/candidates/{id}/replace-resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'HR')")
    public ResponseEntity<AtsCandidateDTO> replaceResume(
            @PathVariable Long id,
            @RequestParam(required = false) String resumePath,
            @RequestParam(required = false) String resumeOriginalName,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) String addressStreet,
            @RequestParam(required = false) String addressArea,
            @RequestParam(required = false) String addressLandmark,
            @RequestParam(required = false) String addressCity,
            @RequestParam(required = false) String addressDistrict,
            @RequestParam(required = false) String addressState,
            @RequestParam(required = false) String addressPostalCode,
            @RequestParam(required = false) String addressCountry,
            @RequestParam(required = false) String appliedProfile,
            @RequestParam(required = false) String officeLocation,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String skills,
            @RequestParam(required = false) String totalExperienceYears,
            @RequestParam(required = false) String linkedinUrl,
            @RequestParam(required = false) String githubUrl,
            @RequestParam(required = false) String educationSummary,
            @RequestParam(required = false) String currentDesignation,
            @RequestParam(required = false) String currentCompanyCv,
            @RequestParam(required = false) String rawResumeText,
            Authentication auth) {
        return ResponseEntity.ok(atsService.replaceResume(
                id, resumePath, resumeOriginalName,
                name, email, phone, address,
                addressStreet, addressArea, addressLandmark,
                addressCity, addressDistrict, addressState,
                addressPostalCode, addressCountry,
                appliedProfile, officeLocation, source,
                skills, totalExperienceYears,
                linkedinUrl, githubUrl,
                educationSummary, currentDesignation,
                currentCompanyCv, rawResumeText,
                auth.getName()));
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'MANAGER', 'ASSISTANT_MANAGER', 'HR')")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(atsService.getStats());
    }
}
