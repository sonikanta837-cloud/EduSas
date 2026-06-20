package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.dto.InterviewCandidateDTO;
import com.emp.management.dto.InterviewFeedbackDTO;
import com.emp.management.dto.InterviewRoundDTO;
import com.emp.management.exception.BadRequestException;
import com.emp.management.exception.GlobalExceptionHandler;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.security.JwtAuthFilter;
import com.emp.management.service.InterviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = InterviewController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class InterviewControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private InterviewService interviewService;

    private InterviewCandidateDTO sampleCandidate;
    private InterviewRoundDTO sampleRound;
    private InterviewFeedbackDTO sampleFeedback;

    @BeforeEach
    void setUp() {
        sampleCandidate = InterviewCandidateDTO.builder()
                .id(1L).name("John Candidate").email("john@example.com")
                .phone("1234567890").position("Engineer").department("Tech")
                .status("PENDING").build();

        sampleRound = InterviewRoundDTO.builder()
                .id(10L).candidateId(1L).roundNumber(1)
                .roundType("TECHNICAL").status("SCHEDULED").interviewerId(2L).build();

        sampleFeedback = InterviewFeedbackDTO.builder()
                .id(100L).roundId(10L).overallRating(4)
                .recommendation("HIRE").comments("Good candidate").build();
    }

    // ── POST /api/interviews/candidates ──────────────────────────────────────

    @Test
    @WithMockUser(username = "hr@company.com", roles = "HR")
    void createCandidate_asHR_returns201() throws Exception {
        when(interviewService.createCandidate(any(), anyString())).thenReturn(sampleCandidate);

        mockMvc.perform(post("/api/interviews/candidates")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(sampleCandidate)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("John Candidate"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void createCandidate_asEmployee_returns403() throws Exception {
        mockMvc.perform(post("/api/interviews/candidates")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(sampleCandidate)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createCandidate_unauthenticated_returns401or403() throws Exception {
        mockMvc.perform(post("/api/interviews/candidates")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(sampleCandidate)))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assert status == 401 || status == 403;
                });
    }

    // ── GET /api/interviews/candidates ───────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllCandidates_asAdmin_returns200WithList() throws Exception {
        when(interviewService.getAllCandidates()).thenReturn(List.of(sampleCandidate));

        mockMvc.perform(get("/api/interviews/candidates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("John Candidate"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void getAllCandidates_asManager_returns200() throws Exception {
        when(interviewService.getAllCandidates()).thenReturn(List.of());

        mockMvc.perform(get("/api/interviews/candidates"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getAllCandidates_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/interviews/candidates"))
                .andExpect(status().isForbidden());
    }

    // ── PUT /api/interviews/candidates/{id}/status ───────────────────────────

    @Test
    @WithMockUser(username = "hr@company.com", roles = "HR")
    void updateCandidateStatus_validStatus_returns200() throws Exception {
        when(interviewService.updateCandidateStatus(eq(1L), anyString(), anyString())).thenReturn(sampleCandidate);

        mockMvc.perform(put("/api/interviews/candidates/1/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("status", "SELECTED"))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "hr@company.com", roles = "HR")
    void updateCandidateStatus_nullStatus_returns400() throws Exception {
        when(interviewService.updateCandidateStatus(eq(1L), isNull(), anyString()))
                .thenThrow(new BadRequestException("Status is required"));

        mockMvc.perform(put("/api/interviews/candidates/1/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "hr@company.com", roles = "HR")
    void updateCandidateStatus_invalidStatus_returns400() throws Exception {
        when(interviewService.updateCandidateStatus(eq(1L), eq("BOGUS"), anyString()))
                .thenThrow(new BadRequestException("Invalid status: BOGUS"));

        mockMvc.perform(put("/api/interviews/candidates/1/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("status", "BOGUS"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "hr@company.com", roles = "HR")
    void updateCandidateStatus_candidateNotFound_returns404() throws Exception {
        when(interviewService.updateCandidateStatus(eq(999L), anyString(), anyString()))
                .thenThrow(new ResourceNotFoundException("Candidate not found: 999"));

        mockMvc.perform(put("/api/interviews/candidates/999/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("status", "SELECTED"))))
                .andExpect(status().isNotFound());
    }

    // ── PUT /api/interviews/rounds/{id}/start ─────────────────────────────────

    @Test
    @WithMockUser(username = "mgr@company.com", roles = "MANAGER")
    void startRound_asInterviewer_returns200() throws Exception {
        when(interviewService.startRound(eq(10L), anyString())).thenReturn(sampleRound);

        mockMvc.perform(put("/api/interviews/rounds/10/start"))
                .andExpect(status().isOk());
    }

    @Test
    void startRound_unauthenticated_returns401or403() throws Exception {
        mockMvc.perform(put("/api/interviews/rounds/10/start"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assert status == 401 || status == 403;
                });
    }

    @Test
    @WithMockUser(username = "hr@company.com", roles = "HR")
    void startRound_nonInterviewer_returns400() throws Exception {
        when(interviewService.startRound(eq(10L), anyString()))
                .thenThrow(new BadRequestException("Only the assigned interviewer can start this round"));

        mockMvc.perform(put("/api/interviews/rounds/10/start"))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/interviews/rounds/{roundId}/feedback ────────────────────────

    @Test
    @WithMockUser(username = "mgr@company.com", roles = "MANAGER")
    void submitFeedback_authenticated_returns201() throws Exception {
        when(interviewService.submitFeedback(eq(10L), any(), anyString())).thenReturn(sampleFeedback);

        mockMvc.perform(post("/api/interviews/rounds/10/feedback")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(sampleFeedback)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.overallRating").value(4));
    }

    @Test
    void submitFeedback_unauthenticated_returns401or403() throws Exception {
        mockMvc.perform(post("/api/interviews/rounds/10/feedback")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(sampleFeedback)))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assert status == 401 || status == 403;
                });
    }

    @Test
    @WithMockUser(username = "mgr@company.com", roles = "MANAGER")
    void submitFeedback_duplicateFeedback_returns400() throws Exception {
        when(interviewService.submitFeedback(eq(10L), any(), anyString()))
                .thenThrow(new BadRequestException("Feedback already submitted for this round"));

        mockMvc.perform(post("/api/interviews/rounds/10/feedback")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(sampleFeedback)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "mgr@company.com", roles = "MANAGER")
    void submitFeedback_strongNoHire_returns201() throws Exception {
        InterviewFeedbackDTO strongNoHire = InterviewFeedbackDTO.builder()
                .roundId(10L).overallRating(1).recommendation("STRONG_NO_HIRE")
                .comments("Not suitable").build();
        when(interviewService.submitFeedback(eq(10L), any(), anyString())).thenReturn(strongNoHire);

        mockMvc.perform(post("/api/interviews/rounds/10/feedback")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(strongNoHire)))
                .andExpect(status().isCreated());
    }

    // ── GET /api/interviews/rounds/{id} ──────────────────────────────────────

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getRound_authenticated_returns200() throws Exception {
        when(interviewService.getRound(10L)).thenReturn(sampleRound);

        mockMvc.perform(get("/api/interviews/rounds/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roundNumber").value(1));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getRound_notFound_returns404() throws Exception {
        when(interviewService.getRound(999L))
                .thenThrow(new ResourceNotFoundException("Interview round not found: 999"));

        mockMvc.perform(get("/api/interviews/rounds/999"))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/interviews/stats ─────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getStats_asAdmin_returns200() throws Exception {
        when(interviewService.getStats()).thenReturn(Map.of("total", 10L, "selected", 3L));

        mockMvc.perform(get("/api/interviews/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(10));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getStats_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/interviews/stats"))
                .andExpect(status().isForbidden());
    }

    // ── DELETE /api/interviews/candidates/{id} ────────────────────────────────

    @Test
    @WithMockUser(username = "admin@company.com", roles = "ADMIN")
    void deleteCandidate_asAdmin_returns204() throws Exception {
        doNothing().when(interviewService).deleteCandidate(eq(1L), anyString());

        mockMvc.perform(delete("/api/interviews/candidates/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "HR")
    void deleteCandidate_asHR_returns403() throws Exception {
        mockMvc.perform(delete("/api/interviews/candidates/1"))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/interviews/my-rounds ────────────────────────────────────────

    @Test
    @WithMockUser(username = "mgr@company.com", roles = "MANAGER")
    void getMyRounds_authenticated_returns200() throws Exception {
        when(interviewService.getMyRounds(anyString())).thenReturn(List.of(sampleRound));

        mockMvc.perform(get("/api/interviews/my-rounds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
