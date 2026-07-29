package com.emp.management.controller;

import com.emp.management.exception.GlobalExceptionHandler;
import com.emp.management.dto.*;
import com.emp.management.security.JwtAuthFilter;
import com.emp.management.service.AtsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Focuses on the permission surface of the ATS pipeline — the ~50 endpoints on
 * InterviewController — rather than AtsService's internal business logic (that
 * service is ~3,900 lines and out of scope for this pass). The highest-value
 * area covered here is the Final Round's two-step decision separation: Director
 * notes (ADMIN/DIRECTOR) vs. the binding HR hiring decision (ADMIN/HR) are two
 * distinct endpoints with two distinct, non-overlapping role sets.
 *
 * Uses a locally-scoped security config (rather than the shared TestSecurityConfig)
 * because this controller uniquely exposes public, no-JWT candidate token endpoints
 * that mirror production SecurityConfig's permitAll list for
 * /api/interviews/technical/candidate/** and /api/interviews/final/candidate/**.
 */
@WebMvcTest(controllers = InterviewController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
@Import({InterviewControllerTest.LocalTestSecurityConfig.class, GlobalExceptionHandler.class})
class InterviewControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class LocalTestSecurityConfig {
        @Bean
        SecurityFilterChain interviewTestSecurityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/interviews/technical/candidate/**").permitAll()
                    .requestMatchers("/api/interviews/final/candidate/**").permitAll()
                    .anyRequest().authenticated());
            return http.build();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private AtsService atsService;

    // ── CV Bank ────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "HR")
    void storeResume_asHR_returns200() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", new byte[]{1, 2, 3});
        when(atsService.storeAndParseResume(any())).thenReturn(Map.of("resumePath", "/uploads/resume.pdf"));

        mockMvc.perform(multipart("/api/interviews/candidates/store-resume").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumePath").value("/uploads/resume.pdf"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void storeResume_asManager_returns403() throws Exception {
        // CV Bank write ops are ADMIN/DIRECTOR/HR only — Manager can view/export but not create
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", new byte[]{1});

        mockMvc.perform(multipart("/api/interviews/candidates/store-resume").file(file))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "hr@company.com", roles = "HR")
    void uploadCandidate_asHR_returns201() throws Exception {
        AtsCandidateDTO dto = AtsCandidateDTO.builder().id(1L).name("Jane Doe").appliedProfile("Backend Engineer").build();
        when(atsService.uploadCandidate(any(), any(), eq("Jane Doe"), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(),
                eq("Backend Engineer"), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                eq("hr@company.com")))
                .thenReturn(dto);

        mockMvc.perform(multipart("/api/interviews/candidates")
                        .param("name", "Jane Doe")
                        .param("appliedProfile", "Backend Engineer"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Jane Doe"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void uploadCandidate_asManager_returns403() throws Exception {
        mockMvc.perform(multipart("/api/interviews/candidates")
                        .param("name", "Jane Doe")
                        .param("appliedProfile", "Backend Engineer"))
                .andExpect(status().isForbidden());

        verify(atsService, never()).uploadCandidate(any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void getAllCandidates_asManager_returns200() throws Exception {
        // Manager can view/export the pipeline, just not create/delete candidates
        when(atsService.getAllCandidates()).thenReturn(List.of());

        mockMvc.perform(get("/api/interviews/candidates"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getAllCandidates_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/interviews/candidates"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HR")
    void deleteCandidate_asHR_returns403() throws Exception {
        // Delete is narrower than create — HR can create but not delete a candidate
        mockMvc.perform(delete("/api/interviews/candidates/1"))
                .andExpect(status().isForbidden());

        verify(atsService, never()).deleteCandidate(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteCandidate_asAdmin_returns204() throws Exception {
        doNothing().when(atsService).deleteCandidate(1L);

        mockMvc.perform(delete("/api/interviews/candidates/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "HR")
    void checkDuplicate_found_returns200() throws Exception {
        AtsCandidateDTO dup = AtsCandidateDTO.builder().id(2L).email("dup@company.com").build();
        when(atsService.checkDuplicate(eq("dup@company.com"), any(), any())).thenReturn(dup);

        mockMvc.perform(post("/api/interviews/candidates/check-duplicate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "dup@company.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("dup@company.com"));
    }

    @Test
    @WithMockUser(roles = "HR")
    void checkDuplicate_notFound_returns204() throws Exception {
        when(atsService.checkDuplicate(any(), any(), any())).thenReturn(null);

        mockMvc.perform(post("/api/interviews/candidates/check-duplicate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "unique@company.com"))))
                .andExpect(status().isNoContent());
    }

    // ── HR Screening ───────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "hr@company.com", roles = "HR")
    void saveHrScreening_asHR_returns200() throws Exception {
        AtsHrScreeningDTO dto = AtsHrScreeningDTO.builder().decision("PENDING").build();
        when(atsService.saveHrScreening(eq(1L), any(), eq("hr@company.com"))).thenReturn(dto);

        mockMvc.perform(post("/api/interviews/candidates/1/hr-screening")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void saveHrScreening_asManager_returns403() throws Exception {
        mockMvc.perform(post("/api/interviews/candidates/1/hr-screening")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AtsHrScreeningDTO.builder().build())))
                .andExpect(status().isForbidden());
    }

    // ── Technical Interviews ───────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "hr@company.com", roles = "HR")
    void assignTechnicalInterview_asHR_returns201() throws Exception {
        AtsTechnicalInterviewDTO dto = AtsTechnicalInterviewDTO.builder().id(10L).build();
        when(atsService.assignTechnicalInterview(eq(1L), any(), eq("hr@company.com"))).thenReturn(dto);

        mockMvc.perform(post("/api/interviews/candidates/1/technical")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void assignTechnicalInterview_asManager_returns403() throws Exception {
        mockMvc.perform(post("/api/interviews/candidates/1/technical")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AtsTechnicalInterviewDTO.builder().build())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "interviewer@company.com", roles = "MANAGER")
    void submitTechnicalFeedback_anyAuthenticatedRole_returns200() throws Exception {
        // Endpoint itself is isAuthenticated() only — service layer is expected to
        // restrict this to the actual assigned interviewer (not verifiable from the
        // controller test alone; documented as a manual/service-level check point).
        AtsCandidateDTO dto = AtsCandidateDTO.builder().id(1L).build();
        when(atsService.submitTechnicalFeedback(eq(10L), any(), eq("interviewer@company.com"))).thenReturn(dto);

        mockMvc.perform(post("/api/interviews/technical/10/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AtsTechnicalInterviewDTO.builder().build())))
                .andExpect(status().isOk());
    }

    @Test
    void myAssignments_unauthenticated_returns401or403() throws Exception {
        mockMvc.perform(get("/api/interviews/my-assignments"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assert status == 401 || status == 403;
                });
    }

    // ── Candidate public endpoints (no JWT) — must NOT require authentication ──

    @Test
    void getCandidateRoom_publicToken_noAuthRequired_returns200() throws Exception {
        when(atsService.getCandidateRoom("tok123")).thenReturn(CandidateRoomDTO.builder().build());

        mockMvc.perform(get("/api/interviews/technical/candidate/tok123"))
                .andExpect(status().isOk());
    }

    @Test
    void startInterview_publicToken_noAuthRequired_returns200() throws Exception {
        doNothing().when(atsService).startInterview("tok123");

        mockMvc.perform(post("/api/interviews/technical/candidate/tok123/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void submitInterview_publicToken_noAuthRequired_returns200() throws Exception {
        when(atsService.submitInterview("tok123")).thenReturn(Map.of("score", 80));

        mockMvc.perform(post("/api/interviews/technical/candidate/tok123/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(80));
    }

    @Test
    void logViolation_publicToken_capturesForwardedForIp() throws Exception {
        when(atsService.logViolation(eq("tok123"), eq("TAB_SWITCH"), any(), eq("203.0.113.5")))
                .thenReturn(Map.of("violationCount", 1));

        mockMvc.perform(post("/api/interviews/technical/candidate/tok123/violation")
                        .header("X-Forwarded-For", "203.0.113.5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("violationType", "TAB_SWITCH"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.violationCount").value(1));
    }

    @Test
    void uploadRecording_publicToken_noAuthRequired_returns200() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "rec.webm", "video/webm", new byte[]{1, 2, 3});
        doNothing().when(atsService).uploadRecording(eq("tok123"), any());

        mockMvc.perform(multipart("/api/interviews/technical/candidate/tok123/recording").file(file))
                .andExpect(status().isOk());
    }

    // ── Final Round — the two-step decision separation ──────────────────────

    @Test
    @WithMockUser(username = "director@company.com", roles = "DIRECTOR")
    void saveFinalRound_asDirector_returns200() throws Exception {
        AtsFinalRoundDTO dto = AtsFinalRoundDTO.builder().directorRecommendation("APPROVE").build();
        when(atsService.saveFinalRound(eq(1L), any(), eq("director@company.com"))).thenReturn(dto);

        mockMvc.perform(post("/api/interviews/candidates/1/final")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.directorRecommendation").value("APPROVE"));
    }

    @Test
    @WithMockUser(roles = "HR")
    void saveFinalRound_asHR_returns403() throws Exception {
        // Director's advisory notes endpoint — HR is explicitly excluded here
        mockMvc.perform(post("/api/interviews/candidates/1/final")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AtsFinalRoundDTO.builder().build())))
                .andExpect(status().isForbidden());

        verify(atsService, never()).saveFinalRound(any(), any(), any());
    }

    @Test
    @WithMockUser(username = "hr@company.com", roles = "HR")
    void submitFinalDecision_asHR_returns200() throws Exception {
        AtsCandidateDTO dto = AtsCandidateDTO.builder().id(1L).status("SELECTED").build();
        when(atsService.submitFinalDecision(eq(1L), any(), eq("hr@company.com"))).thenReturn(dto);

        mockMvc.perform(post("/api/interviews/final/1/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AtsFinalRoundDTO.builder().finalDecision("APPROVE").build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SELECTED"));
    }

    @Test
    @WithMockUser(roles = "DIRECTOR")
    void submitFinalDecision_asDirector_returns403() throws Exception {
        // The binding hiring decision is HR's alone (plus Admin) — Director's role here
        // is limited to advisory notes via /candidates/{id}/final, a separate endpoint.
        mockMvc.perform(post("/api/interviews/final/1/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AtsFinalRoundDTO.builder().finalDecision("APPROVE").build())))
                .andExpect(status().isForbidden());

        verify(atsService, never()).submitFinalDecision(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void submitFinalDecision_asManager_returns403() throws Exception {
        mockMvc.perform(post("/api/interviews/final/1/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AtsFinalRoundDTO.builder().build())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "hr@company.com", roles = "HR")
    void assignFinalRoundDirector_asHR_returns200() throws Exception {
        AtsFinalRoundDTO dto = AtsFinalRoundDTO.builder().assignedByName("HR User").build();
        when(atsService.assignFinalRoundDirector(eq(1L), eq(5L), eq("hr@company.com"))).thenReturn(dto);

        mockMvc.perform(post("/api/interviews/candidates/1/final/assign-director")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("directorId", 5))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "DIRECTOR")
    void assignFinalRoundDirector_asDirector_returns403() throws Exception {
        // Assigning a Director for the Final Round is an HR/Admin action, not the Director's own
        mockMvc.perform(post("/api/interviews/candidates/1/final/assign-director")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("directorId", 5))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "director@company.com", roles = "DIRECTOR")
    void generateFinalInterviewLink_asDirector_returns200() throws Exception {
        AtsFinalRoundDTO dto = AtsFinalRoundDTO.builder().token("abc123").interviewStatus("LINK_GENERATED").build();
        when(atsService.generateFinalInterviewLink(eq(1L), any(), eq("director@company.com"))).thenReturn(dto);

        mockMvc.perform(post("/api/interviews/candidates/1/final/generate-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("scheduledAt", ""))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interviewStatus").value("LINK_GENERATED"));
    }

    @Test
    @WithMockUser(roles = "HR")
    void generateFinalInterviewLink_asHR_returns403() throws Exception {
        // Only the assigned Director (or Admin) generates the final-round link
        mockMvc.perform(post("/api/interviews/candidates/1/final/generate-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isForbidden());
    }

    // ── Directors list ─────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "HR")
    void getEligibleDirectors_asHR_returns200() throws Exception {
        when(atsService.getEligibleDirectors()).thenReturn(List.of());

        mockMvc.perform(get("/api/interviews/directors"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getEligibleDirectors_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/interviews/directors"))
                .andExpect(status().isForbidden());
    }

    // ── Stats ──────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "MANAGER")
    void getStats_asManager_returns200() throws Exception {
        when(atsService.getStats()).thenReturn(Map.of("total", 42));

        mockMvc.perform(get("/api/interviews/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(42));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getStats_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/interviews/stats"))
                .andExpect(status().isForbidden());
    }

    // ── Resume download ────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "MANAGER")
    void downloadResume_asManager_returns200() throws Exception {
        Resource file = new ByteArrayResource("pdf-bytes".getBytes()) {
            @Override public String getFilename() { return "resume.pdf"; }
        };
        when(atsService.downloadResume(1L)).thenReturn(
                org.springframework.http.ResponseEntity.ok(file));

        mockMvc.perform(get("/api/interviews/candidates/1/resume"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void downloadResume_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/interviews/candidates/1/resume"))
                .andExpect(status().isForbidden());
    }
}
