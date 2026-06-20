package com.emp.management.service;

import com.emp.management.dto.InterviewCandidateDTO;
import com.emp.management.dto.InterviewFeedbackDTO;
import com.emp.management.dto.InterviewRoundDTO;
import com.emp.management.entity.*;
import com.emp.management.exception.BadRequestException;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InterviewServiceTest {

    @Mock private InterviewCandidateRepository candidateRepo;
    @Mock private InterviewRoundRepository roundRepo;
    @Mock private InterviewFeedbackRepository feedbackRepo;
    @Mock private InterviewAuditLogRepository auditRepo;
    @Mock private EmployeeDetailsRepository employeeRepo;
    @Mock private UserRepository userRepo;
    @Mock private EmailService emailService;

    @InjectMocks private InterviewService interviewService;

    private User hrUser;
    private EmployeeDetails hrEmployee;
    private User interviewerUser;
    private EmployeeDetails interviewer;
    private InterviewCandidate candidate;
    private InterviewRound round;

    @BeforeEach
    void setUp() {
        hrUser = User.builder().id(1L).email("hr@company.com").role(Role.HR).active(true).build();
        hrEmployee = EmployeeDetails.builder().id(1L).user(hrUser)
                .firstName("HR").lastName("Person").active(true).build();
        hrUser.setEmployeeDetails(hrEmployee);

        interviewerUser = User.builder().id(2L).email("mgr@company.com").role(Role.MANAGER).active(true).build();
        interviewer = EmployeeDetails.builder().id(2L).user(interviewerUser)
                .firstName("Jane").lastName("Manager").active(true).build();
        interviewerUser.setEmployeeDetails(interviewer);

        candidate = InterviewCandidate.builder()
                .id(10L)
                .name("John Candidate")
                .email("john@example.com")
                .phone("9876543210")
                .position("Software Engineer")
                .department("Engineering")
                .status(CandidateStatus.PENDING)
                .createdBy(hrEmployee)
                .build();

        round = InterviewRound.builder()
                .id(100L)
                .candidate(candidate)
                .roundNumber(1)
                .roundType(RoundType.TECHNICAL)
                .scheduledAt(LocalDateTime.now().plusDays(1))
                .durationMinutes(60)
                .location("Online")
                .status(RoundStatus.SCHEDULED)
                .interviewer(interviewer)
                .assignedBy(hrEmployee)
                .build();
    }

    private void mockCallerLookup(String email, EmployeeDetails emp) {
        when(userRepo.findByEmail(email)).thenReturn(Optional.of(emp.getUser()));
        when(employeeRepo.findByUserId(emp.getUser().getId())).thenReturn(Optional.of(emp));
    }

    // ── createCandidate ───────────────────────────────────────────────────────

    @Test
    void createCandidate_validDTO_returnsSavedCandidate() {
        InterviewCandidateDTO dto = InterviewCandidateDTO.builder()
                .name("Alice New").email("alice@ex.com").phone("1234567890")
                .position("QA Engineer").department("QA").source("LinkedIn").build();

        mockCallerLookup("hr@company.com", hrEmployee);
        when(candidateRepo.save(any())).thenReturn(candidate);
        lenient().when(auditRepo.save(any())).thenReturn(null);

        InterviewCandidateDTO result = interviewService.createCandidate(dto, "hr@company.com");

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("John Candidate");
        verify(candidateRepo).save(any(InterviewCandidate.class));
    }

    @Test
    void createCandidate_callerNotFound_throwsResourceNotFoundException() {
        when(userRepo.findByEmail("nobody@company.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> interviewService.createCandidate(
                InterviewCandidateDTO.builder().name("X").build(), "nobody@company.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── updateCandidateStatus ─────────────────────────────────────────────────

    @Test
    void updateCandidateStatus_validStatus_updatesAndReturnsDTO() {
        mockCallerLookup("hr@company.com", hrEmployee);
        when(candidateRepo.findById(10L)).thenReturn(Optional.of(candidate));
        when(candidateRepo.save(any())).thenReturn(candidate);
        when(auditRepo.save(any())).thenReturn(null);
        when(roundRepo.findByCandidateIdOrderByRoundNumberAsc(anyLong())).thenReturn(List.of());
        when(userRepo.findByRoleIn(anyList())).thenReturn(List.of());
        doNothing().when(emailService).sendCandidateDecisionEmail(any(), anyString(), anyString(), anyString(), anyString());

        InterviewCandidateDTO result = interviewService.updateCandidateStatus(10L, "SELECTED", "hr@company.com");

        assertThat(result).isNotNull();
    }

    @Test
    void updateCandidateStatus_nullStatus_throwsBadRequestException() {
        mockCallerLookup("hr@company.com", hrEmployee);
        when(candidateRepo.findById(10L)).thenReturn(Optional.of(candidate));

        assertThatThrownBy(() -> interviewService.updateCandidateStatus(10L, null, "hr@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Status is required");
    }

    @Test
    void updateCandidateStatus_blankStatus_throwsBadRequestException() {
        mockCallerLookup("hr@company.com", hrEmployee);
        when(candidateRepo.findById(10L)).thenReturn(Optional.of(candidate));

        assertThatThrownBy(() -> interviewService.updateCandidateStatus(10L, "   ", "hr@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Status is required");
    }

    @Test
    void updateCandidateStatus_invalidStatus_throwsBadRequestException() {
        mockCallerLookup("hr@company.com", hrEmployee);
        when(candidateRepo.findById(10L)).thenReturn(Optional.of(candidate));

        assertThatThrownBy(() -> interviewService.updateCandidateStatus(10L, "INVALID_STATUS", "hr@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid status");
    }

    @Test
    void updateCandidateStatus_candidateNotFound_throwsResourceNotFoundException() {
        mockCallerLookup("hr@company.com", hrEmployee);
        when(candidateRepo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> interviewService.updateCandidateStatus(999L, "SELECTED", "hr@company.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateCandidateStatus_allValidStatuses_succeed() {
        String[] validStatuses = {"PENDING", "IN_PROGRESS", "SELECTED", "REJECTED", "ON_HOLD"};

        for (String status : validStatuses) {
            candidate.setStatus(CandidateStatus.PENDING);
            mockCallerLookup("hr@company.com", hrEmployee);
            when(candidateRepo.findById(10L)).thenReturn(Optional.of(candidate));
            when(candidateRepo.save(any())).thenReturn(candidate);
            when(auditRepo.save(any())).thenReturn(null);
            when(roundRepo.findByCandidateIdOrderByRoundNumberAsc(anyLong())).thenReturn(List.of());
            when(userRepo.findByRoleIn(anyList())).thenReturn(List.of());
            lenient().doNothing().when(emailService).sendCandidateDecisionEmail(any(), anyString(), anyString(), anyString(), anyString());

            InterviewCandidateDTO result = interviewService.updateCandidateStatus(10L, status, "hr@company.com");
            assertThat(result).isNotNull();
        }
    }

    // ── startRound ────────────────────────────────────────────────────────────

    @Test
    void startRound_byAssignedInterviewer_setsInProgress() {
        mockCallerLookup("mgr@company.com", interviewer);
        when(roundRepo.findById(100L)).thenReturn(Optional.of(round));
        when(roundRepo.save(any())).thenReturn(round);
        when(auditRepo.save(any())).thenReturn(null);
        when(feedbackRepo.findByRoundId(anyLong())).thenReturn(List.of());

        InterviewRoundDTO result = interviewService.startRound(100L, "mgr@company.com");

        assertThat(result).isNotNull();
        verify(roundRepo).save(any(InterviewRound.class));
    }

    @Test
    void startRound_byNonAssignedPerson_throwsBadRequestException() {
        // HR is not the interviewer for this round
        mockCallerLookup("hr@company.com", hrEmployee);
        when(roundRepo.findById(100L)).thenReturn(Optional.of(round));

        assertThatThrownBy(() -> interviewService.startRound(100L, "hr@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("assigned interviewer");
    }

    @Test
    void startRound_roundNotFound_throwsResourceNotFoundException() {
        mockCallerLookup("mgr@company.com", interviewer);
        when(roundRepo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> interviewService.startRound(999L, "mgr@company.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── submitFeedback ────────────────────────────────────────────────────────

    @Test
    void submitFeedback_validFeedback_savesAndReturnsDTO() {
        round.setStatus(RoundStatus.IN_PROGRESS);
        InterviewFeedbackDTO dto = InterviewFeedbackDTO.builder()
                .overallRating(4).technicalRating(4).communicationRating(3)
                .recommendation("HIRE").strengths("Good coding").build();

        InterviewFeedback savedFeedback = InterviewFeedback.builder()
                .id(200L).round(round).submittedBy(interviewer)
                .overallRating(4).recommendation(Recommendation.HIRE).build();

        mockCallerLookup("mgr@company.com", interviewer);
        when(roundRepo.findById(100L)).thenReturn(Optional.of(round));
        when(feedbackRepo.existsByRoundIdAndSubmittedById(100L, 2L)).thenReturn(false);
        when(feedbackRepo.save(any())).thenReturn(savedFeedback);
        when(roundRepo.save(any())).thenReturn(round);
        when(auditRepo.save(any())).thenReturn(null);
        doNothing().when(emailService).sendInterviewFeedbackEmail(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());

        InterviewFeedbackDTO result = interviewService.submitFeedback(100L, dto, "mgr@company.com");

        assertThat(result).isNotNull();
        assertThat(result.getOverallRating()).isEqualTo(4);
        verify(feedbackRepo).save(any(InterviewFeedback.class));
    }

    @Test
    void submitFeedback_strongNoHire_acceptedRecommendation() {
        round.setStatus(RoundStatus.IN_PROGRESS);
        InterviewFeedbackDTO dto = InterviewFeedbackDTO.builder()
                .overallRating(1).recommendation("STRONG_NO_HIRE").build();

        InterviewFeedback savedFeedback = InterviewFeedback.builder()
                .id(201L).round(round).submittedBy(interviewer)
                .overallRating(1).recommendation(Recommendation.STRONG_NO_HIRE).build();

        mockCallerLookup("mgr@company.com", interviewer);
        when(roundRepo.findById(100L)).thenReturn(Optional.of(round));
        when(feedbackRepo.existsByRoundIdAndSubmittedById(100L, 2L)).thenReturn(false);
        when(feedbackRepo.save(any())).thenReturn(savedFeedback);
        when(roundRepo.save(any())).thenReturn(round);
        when(auditRepo.save(any())).thenReturn(null);
        doNothing().when(emailService).sendInterviewFeedbackEmail(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());

        InterviewFeedbackDTO result = interviewService.submitFeedback(100L, dto, "mgr@company.com");

        // STRONG_NO_HIRE is a valid Recommendation enum value — no exception thrown
        assertThat(result.getRecommendation()).isEqualTo("STRONG_NO_HIRE");
        verify(feedbackRepo).save(any(InterviewFeedback.class));
    }

    @Test
    void submitFeedback_byNonInterviewer_throwsBadRequestException() {
        mockCallerLookup("hr@company.com", hrEmployee);
        when(roundRepo.findById(100L)).thenReturn(Optional.of(round));

        assertThatThrownBy(() -> interviewService.submitFeedback(100L,
                InterviewFeedbackDTO.builder().overallRating(3).build(), "hr@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("assigned interviewer");
    }

    @Test
    void submitFeedback_duplicateFeedback_throwsBadRequestException() {
        mockCallerLookup("mgr@company.com", interviewer);
        when(roundRepo.findById(100L)).thenReturn(Optional.of(round));
        when(feedbackRepo.existsByRoundIdAndSubmittedById(100L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> interviewService.submitFeedback(100L,
                InterviewFeedbackDTO.builder().overallRating(3).build(), "mgr@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already submitted");
    }

    @Test
    void submitFeedback_cancelledRound_throwsBadRequestException() {
        round.setStatus(RoundStatus.CANCELLED);
        mockCallerLookup("mgr@company.com", interviewer);
        when(roundRepo.findById(100L)).thenReturn(Optional.of(round));
        when(feedbackRepo.existsByRoundIdAndSubmittedById(100L, 2L)).thenReturn(false);

        assertThatThrownBy(() -> interviewService.submitFeedback(100L,
                InterviewFeedbackDTO.builder().overallRating(3).build(), "mgr@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cancelled round");
    }

    @Test
    void submitFeedback_invalidRecommendation_throwsBadRequestException() {
        mockCallerLookup("mgr@company.com", interviewer);
        when(roundRepo.findById(100L)).thenReturn(Optional.of(round));
        when(feedbackRepo.existsByRoundIdAndSubmittedById(100L, 2L)).thenReturn(false);

        assertThatThrownBy(() -> interviewService.submitFeedback(100L,
                InterviewFeedbackDTO.builder().overallRating(3).recommendation("INVALID_VALUE").build(),
                "mgr@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid recommendation");
    }

    // ── getStats — uses DB aggregates, not in-memory ──────────────────────────

    @Test
    void getStats_returnsAggregatedCountsFromRepo() {
        when(candidateRepo.count()).thenReturn(10L);
        when(candidateRepo.countByStatus(CandidateStatus.PENDING)).thenReturn(2L);
        when(candidateRepo.countByStatus(CandidateStatus.IN_PROGRESS)).thenReturn(3L);
        when(candidateRepo.countByStatus(CandidateStatus.SELECTED)).thenReturn(3L);
        when(candidateRepo.countByStatus(CandidateStatus.REJECTED)).thenReturn(2L);
        when(candidateRepo.countByStatus(CandidateStatus.ON_HOLD)).thenReturn(0L);
        when(roundRepo.count()).thenReturn(5L);
        when(roundRepo.countByStatus(RoundStatus.COMPLETED)).thenReturn(3L);
        when(roundRepo.countByStatus(RoundStatus.SCHEDULED)).thenReturn(2L);
        when(feedbackRepo.getAverageOverallRating()).thenReturn(4.2);

        Map<String, Object> stats = interviewService.getStats();

        assertThat(stats.get("total")).isEqualTo(10L);
        assertThat(stats.get("selected")).isEqualTo(3L);
        assertThat(stats.get("totalRounds")).isEqualTo(5L);
        assertThat(stats.get("averageRating")).isEqualTo(4.2);
        // selectionRate should be 30% (3/10 * 100)
        assertThat(((Number) stats.get("selectionRate")).longValue()).isEqualTo(30L);
    }

    @Test
    void getStats_zeroCandidates_returnsZeroRates() {
        when(candidateRepo.count()).thenReturn(0L);
        when(candidateRepo.countByStatus(any())).thenReturn(0L);
        when(roundRepo.count()).thenReturn(0L);
        when(roundRepo.countByStatus(any())).thenReturn(0L);
        when(feedbackRepo.getAverageOverallRating()).thenReturn(null);

        Map<String, Object> stats = interviewService.getStats();

        // When total is 0, selectionRate is 0 (integer 0, not 0L)
        assertThat(((Number) stats.get("selectionRate")).intValue()).isEqualTo(0);
        assertThat(((Number) stats.get("averageRating")).intValue()).isEqualTo(0);
    }

    // ── scheduleRound ─────────────────────────────────────────────────────────

    @Test
    void scheduleRound_validRequest_createsRound() {
        InterviewRoundDTO dto = InterviewRoundDTO.builder()
                .interviewerId(2L).roundType("TECHNICAL")
                .scheduledAt(LocalDateTime.now().plusDays(1))
                .durationMinutes(60).location("Online").build();

        mockCallerLookup("hr@company.com", hrEmployee);
        when(candidateRepo.findById(10L)).thenReturn(Optional.of(candidate));
        when(employeeRepo.findById(2L)).thenReturn(Optional.of(interviewer));
        when(roundRepo.findMaxRoundNumberByCandidateId(10L)).thenReturn(null);
        when(roundRepo.save(any())).thenReturn(round);
        when(candidateRepo.save(any())).thenReturn(candidate);
        when(auditRepo.save(any())).thenReturn(null);
        when(feedbackRepo.findByRoundId(anyLong())).thenReturn(List.of());
        doNothing().when(emailService).sendInterviewAssignedEmail(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString());

        InterviewRoundDTO result = interviewService.scheduleRound(10L, dto, "hr@company.com");

        assertThat(result).isNotNull();
        verify(roundRepo).save(any(InterviewRound.class));
    }

    @Test
    void scheduleRound_invalidRoundType_throwsBadRequestException() {
        InterviewRoundDTO dto = InterviewRoundDTO.builder()
                .interviewerId(2L).roundType("INVALID_TYPE")
                .scheduledAt(LocalDateTime.now().plusDays(1)).build();

        mockCallerLookup("hr@company.com", hrEmployee);
        when(candidateRepo.findById(10L)).thenReturn(Optional.of(candidate));
        when(employeeRepo.findById(2L)).thenReturn(Optional.of(interviewer));

        assertThatThrownBy(() -> interviewService.scheduleRound(10L, dto, "hr@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid round type");
    }

    // ── deleteCandidate ───────────────────────────────────────────────────────

    @Test
    void deleteCandidate_existingCandidate_deletesSuccessfully() {
        mockCallerLookup("hr@company.com", hrEmployee);
        when(candidateRepo.findById(10L)).thenReturn(Optional.of(candidate));
        when(auditRepo.save(any())).thenReturn(null);
        doNothing().when(candidateRepo).delete(candidate);

        interviewService.deleteCandidate(10L, "hr@company.com");

        verify(candidateRepo).delete(candidate);
    }

    @Test
    void deleteCandidate_notFound_throwsResourceNotFoundException() {
        mockCallerLookup("hr@company.com", hrEmployee);
        when(candidateRepo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> interviewService.deleteCandidate(999L, "hr@company.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getAllCandidates ───────────────────────────────────────────────────────

    @Test
    void getAllCandidates_noCandidates_returnsEmptyList() {
        when(candidateRepo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

        List<InterviewCandidateDTO> result = interviewService.getAllCandidates();

        assertThat(result).isEmpty();
    }

    @Test
    void getAllCandidates_withCandidates_returnsList() {
        when(candidateRepo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(candidate));
        when(roundRepo.findByCandidateIdsOrderByRoundNumberAsc(anyList())).thenReturn(List.of());

        List<InterviewCandidateDTO> result = interviewService.getAllCandidates();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("John Candidate");
    }
}
