package com.emp.management.service;

import com.emp.management.dto.InterviewAuditLogDTO;
import com.emp.management.dto.InterviewCandidateDTO;
import com.emp.management.dto.InterviewFeedbackDTO;
import com.emp.management.dto.InterviewRoundDTO;
import com.emp.management.entity.*;
import com.emp.management.exception.BadRequestException;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewService {

    private final InterviewCandidateRepository candidateRepo;
    private final InterviewRoundRepository roundRepo;
    private final InterviewFeedbackRepository feedbackRepo;
    private final InterviewAuditLogRepository auditRepo;
    private final EmployeeDetailsRepository employeeRepo;
    private final UserRepository userRepo;
    private final EmailService emailService;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    // ─────────────────────────────────────────────────────────────────────────
    // Candidate CRUD
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public InterviewCandidateDTO createCandidate(InterviewCandidateDTO dto, String callerEmail) {
        EmployeeDetails creator = findEmployeeByEmailOptional(callerEmail).orElse(null);
        InterviewCandidate candidate = InterviewCandidate.builder()
                .name(dto.getName())
                .email(dto.getEmail())
                .phone(dto.getPhone())
                .position(dto.getPosition())
                .department(dto.getDepartment())
                .source(dto.getSource())
                .notes(dto.getNotes())
                .status(CandidateStatus.PENDING)
                .createdBy(creator)
                .build();
        candidate = candidateRepo.save(candidate);
        audit("CANDIDATE", candidate.getId(), "CANDIDATE_CREATED",
              creator, "Created candidate: " + candidate.getName() + " for " + candidate.getPosition());
        return toCandidateDTO(candidate, false);
    }

    @Transactional(readOnly = true)
    public List<InterviewCandidateDTO> getAllCandidates() {
        List<InterviewCandidate> candidates = candidateRepo.findAllByOrderByCreatedAtDesc();
        if (candidates.isEmpty()) return Collections.emptyList();

        List<Long> candidateIds = candidates.stream().map(InterviewCandidate::getId).collect(Collectors.toList());
        List<InterviewRound> allRounds = roundRepo.findByCandidateIdsOrderByRoundNumberAsc(candidateIds);

        Map<Long, List<InterviewRound>> roundsByCandidate = allRounds.stream()
                .collect(Collectors.groupingBy(r -> r.getCandidate().getId()));

        List<Long> roundIds = allRounds.stream().map(InterviewRound::getId).collect(Collectors.toList());
        Map<Long, List<InterviewFeedback>> feedbacksByRound = roundIds.isEmpty()
                ? Collections.emptyMap()
                : feedbackRepo.findByRoundIdIn(roundIds).stream()
                        .collect(Collectors.groupingBy(f -> f.getRound().getId()));

        return candidates.stream()
                .map(c -> toCandidateDTO(c, roundsByCandidate.getOrDefault(c.getId(), Collections.emptyList()), feedbacksByRound))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public InterviewCandidateDTO getCandidate(Long id) {
        InterviewCandidate c = findCandidate(id);
        return toCandidateDTO(c, true);
    }

    @Transactional
    public InterviewCandidateDTO updateCandidate(Long id, InterviewCandidateDTO dto, String callerEmail) {
        EmployeeDetails caller = getEmployeeByEmail(callerEmail);
        InterviewCandidate candidate = findCandidate(id);
        candidate.setName(dto.getName());
        candidate.setEmail(dto.getEmail());
        candidate.setPhone(dto.getPhone());
        candidate.setPosition(dto.getPosition());
        candidate.setDepartment(dto.getDepartment());
        candidate.setSource(dto.getSource());
        candidate.setNotes(dto.getNotes());
        candidate = candidateRepo.save(candidate);
        audit("CANDIDATE", id, "CANDIDATE_UPDATED", caller, "Updated candidate: " + candidate.getName());
        return toCandidateDTO(candidate, false);
    }

    @Transactional
    public void deleteCandidate(Long id, String callerEmail) {
        EmployeeDetails caller = getEmployeeByEmail(callerEmail);
        InterviewCandidate candidate = findCandidate(id);
        audit("CANDIDATE", id, "CANDIDATE_DELETED", caller, "Deleted candidate: " + candidate.getName());
        candidateRepo.delete(candidate);
    }

    @Transactional
    public InterviewCandidateDTO updateCandidateStatus(Long id, String status, String callerEmail) {
        EmployeeDetails caller = getEmployeeByEmail(callerEmail);
        InterviewCandidate candidate = findCandidate(id);
        if (status == null || status.isBlank()) {
            throw new BadRequestException("Status is required");
        }
        CandidateStatus newStatus;
        try {
            newStatus = CandidateStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status: " + status);
        }
        CandidateStatus oldStatus = candidate.getStatus();
        candidate.setStatus(newStatus);
        candidate = candidateRepo.save(candidate);
        audit("CANDIDATE", id, "STATUS_CHANGED", caller,
              "Status changed from " + oldStatus + " to " + newStatus + " for candidate: " + candidate.getName());

        // Send decision notification email to relevant people
        if (newStatus == CandidateStatus.SELECTED || newStatus == CandidateStatus.REJECTED || newStatus == CandidateStatus.ON_HOLD) {
            sendDecisionNotification(candidate, newStatus.name(), caller);
        }

        return toCandidateDTO(candidate, true);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Round management
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public InterviewRoundDTO scheduleRound(Long candidateId, InterviewRoundDTO dto, String callerEmail) {
        EmployeeDetails caller = getEmployeeByEmail(callerEmail);
        InterviewCandidate candidate = findCandidate(candidateId);
        EmployeeDetails interviewer = employeeRepo.findById(dto.getInterviewerId())
                .orElseThrow(() -> new ResourceNotFoundException("Interviewer not found"));

        int nextRound = Optional.ofNullable(roundRepo.findMaxRoundNumberByCandidateId(candidateId)).orElse(0) + 1;

        RoundType roundType;
        try {
            roundType = RoundType.valueOf(dto.getRoundType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid round type: " + dto.getRoundType());
        }

        InterviewRound round = InterviewRound.builder()
                .candidate(candidate)
                .roundNumber(nextRound)
                .roundType(roundType)
                .scheduledAt(dto.getScheduledAt())
                .durationMinutes(dto.getDurationMinutes())
                .location(dto.getLocation())
                .status(RoundStatus.SCHEDULED)
                .interviewer(interviewer)
                .assignedBy(caller)
                .managerNotes(dto.getManagerNotes())
                .build();
        round = roundRepo.save(round);

        if (candidate.getStatus() == CandidateStatus.PENDING) {
            candidate.setStatus(CandidateStatus.IN_PROGRESS);
            candidateRepo.save(candidate);
        }

        audit("ROUND", round.getId(), "ROUND_SCHEDULED", caller,
              "Round " + nextRound + " (" + roundType + ") scheduled for " + candidate.getName()
              + " — Interviewer: " + interviewer.getFirstName() + " " + interviewer.getLastName());

        // Email the interviewer
        try {
            String interviewerEmail = interviewer.getUser() != null ? interviewer.getUser().getEmail() : null;
            if (interviewerEmail != null) {
                emailService.sendInterviewAssignedEmail(
                        interviewerEmail,
                        interviewer.getFirstName() + " " + interviewer.getLastName(),
                        candidate.getName(),
                        candidate.getPosition(),
                        roundType.name(),
                        dto.getScheduledAt() != null ? dto.getScheduledAt().format(DT_FMT) : "TBD",
                        dto.getLocation(),
                        dto.getManagerNotes()
                );
            }
        } catch (Exception e) {
            log.error("Failed to send interview assignment email: {}", e.getMessage());
        }

        return toRoundDTO(round, true);
    }

    @Transactional(readOnly = true)
    public List<InterviewRoundDTO> getRoundsForCandidate(Long candidateId) {
        return roundRepo.findByCandidateIdOrderByRoundNumberAsc(candidateId)
                .stream().map(r -> toRoundDTO(r, true)).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InterviewRoundDTO> getMyRounds(String callerEmail) {
        EmployeeDetails emp = getEmployeeByEmail(callerEmail);
        return roundRepo.findByInterviewerIdOrderByScheduledAtDesc(emp.getId())
                .stream().map(r -> toRoundDTO(r, true)).collect(Collectors.toList());
    }

    @Transactional
    public InterviewRoundDTO updateRound(Long roundId, InterviewRoundDTO dto, String callerEmail) {
        EmployeeDetails caller = getEmployeeByEmail(callerEmail);
        InterviewRound round = findRound(roundId);

        if (dto.getInterviewerId() != null && !dto.getInterviewerId().equals(
                round.getInterviewer() != null ? round.getInterviewer().getId() : null)) {
            EmployeeDetails newInterviewer = employeeRepo.findById(dto.getInterviewerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Interviewer not found"));
            round.setInterviewer(newInterviewer);
        }

        if (dto.getRoundType() != null) {
            try { round.setRoundType(RoundType.valueOf(dto.getRoundType().toUpperCase())); }
            catch (IllegalArgumentException e) { throw new BadRequestException("Invalid round type"); }
        }
        if (dto.getScheduledAt() != null) round.setScheduledAt(dto.getScheduledAt());
        if (dto.getDurationMinutes() != null) round.setDurationMinutes(dto.getDurationMinutes());
        if (dto.getLocation() != null) round.setLocation(dto.getLocation());
        if (dto.getManagerNotes() != null) round.setManagerNotes(dto.getManagerNotes());
        if (dto.getStatus() != null) {
            try { round.setStatus(RoundStatus.valueOf(dto.getStatus().toUpperCase())); }
            catch (IllegalArgumentException e) { throw new BadRequestException("Invalid round status"); }
        }

        round = roundRepo.save(round);
        audit("ROUND", roundId, "ROUND_UPDATED", caller,
              "Round " + round.getRoundNumber() + " updated for " + round.getCandidate().getName());
        return toRoundDTO(round, true);
    }

    @Transactional
    public InterviewRoundDTO startRound(Long roundId, String callerEmail) {
        EmployeeDetails caller = getEmployeeByEmail(callerEmail);
        InterviewRound round = findRound(roundId);
        if (round.getInterviewer() == null || !round.getInterviewer().getId().equals(caller.getId())) {
            throw new BadRequestException("Only the assigned interviewer can start this round");
        }
        round.setStatus(RoundStatus.IN_PROGRESS);
        round = roundRepo.save(round);
        audit("ROUND", roundId, "ROUND_STARTED", caller,
              "Round " + round.getRoundNumber() + " started for " + round.getCandidate().getName());
        return toRoundDTO(round, true);
    }

    @Transactional
    public void cancelRound(Long roundId, String callerEmail) {
        EmployeeDetails caller = getEmployeeByEmail(callerEmail);
        InterviewRound round = findRound(roundId);
        round.setStatus(RoundStatus.CANCELLED);
        roundRepo.save(round);
        audit("ROUND", roundId, "ROUND_CANCELLED", caller,
              "Round " + round.getRoundNumber() + " cancelled for " + round.getCandidate().getName());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Feedback
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public InterviewFeedbackDTO submitFeedback(Long roundId, InterviewFeedbackDTO dto, String callerEmail) {
        EmployeeDetails caller = getEmployeeByEmail(callerEmail);
        InterviewRound round = findRound(roundId);

        if (round.getInterviewer() == null || !round.getInterviewer().getId().equals(caller.getId())) {
            throw new BadRequestException("Only the assigned interviewer can submit feedback for this round");
        }
        if (feedbackRepo.existsByRoundIdAndSubmittedById(roundId, caller.getId())) {
            throw new BadRequestException("Feedback already submitted for this round");
        }
        if (round.getStatus() == RoundStatus.CANCELLED) {
            throw new BadRequestException("Cannot submit feedback for a cancelled round");
        }

        Recommendation recommendation = null;
        if (dto.getRecommendation() != null) {
            try { recommendation = Recommendation.valueOf(dto.getRecommendation().toUpperCase()); }
            catch (IllegalArgumentException e) { throw new BadRequestException("Invalid recommendation value"); }
        }

        InterviewFeedback feedback = InterviewFeedback.builder()
                .round(round)
                .submittedBy(caller)
                .overallRating(dto.getOverallRating())
                .technicalRating(dto.getTechnicalRating())
                .communicationRating(dto.getCommunicationRating())
                .problemSolvingRating(dto.getProblemSolvingRating())
                .cultureFitRating(dto.getCultureFitRating())
                .strengths(dto.getStrengths())
                .weaknesses(dto.getWeaknesses())
                .comments(dto.getComments())
                .recommendation(recommendation)
                .projectFit(dto.getProjectFit())
                .suspectedCheating(dto.getSuspectedCheating())
                .build();
        feedback = feedbackRepo.save(feedback);

        round.setStatus(RoundStatus.COMPLETED);
        roundRepo.save(round);

        audit("FEEDBACK", feedback.getId(), "FEEDBACK_SUBMITTED", caller,
              "Feedback submitted by " + caller.getFirstName() + " " + caller.getLastName()
              + " for round " + round.getRoundNumber() + " — Candidate: " + round.getCandidate().getName());

        // Notify manager who assigned the round
        try {
            EmployeeDetails mgr = round.getAssignedBy();
            if (mgr != null && mgr.getUser() != null) {
                emailService.sendInterviewFeedbackEmail(
                        mgr.getUser().getEmail(),
                        mgr.getFirstName() + " " + mgr.getLastName(),
                        caller.getFirstName() + " " + caller.getLastName(),
                        round.getCandidate().getName(),
                        round.getRoundType().name(),
                        dto.getRecommendation(),
                        dto.getOverallRating() != null ? dto.getOverallRating().toString() : "N/A"
                );
            }
        } catch (Exception e) {
            log.error("Failed to send feedback notification email: {}", e.getMessage());
        }

        return toFeedbackDTO(feedback);
    }

    @Transactional
    public InterviewFeedbackDTO updateFeedback(Long feedbackId, InterviewFeedbackDTO dto, String callerEmail) {
        EmployeeDetails caller = getEmployeeByEmail(callerEmail);
        InterviewFeedback feedback = feedbackRepo.findById(feedbackId)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback not found"));

        if (!feedback.getSubmittedBy().getId().equals(caller.getId())) {
            throw new BadRequestException("You can only edit your own feedback");
        }

        Recommendation recommendation = null;
        if (dto.getRecommendation() != null) {
            try { recommendation = Recommendation.valueOf(dto.getRecommendation().toUpperCase()); }
            catch (IllegalArgumentException e) { throw new BadRequestException("Invalid recommendation value"); }
        }

        feedback.setOverallRating(dto.getOverallRating());
        feedback.setTechnicalRating(dto.getTechnicalRating());
        feedback.setCommunicationRating(dto.getCommunicationRating());
        feedback.setProblemSolvingRating(dto.getProblemSolvingRating());
        feedback.setCultureFitRating(dto.getCultureFitRating());
        feedback.setStrengths(dto.getStrengths());
        feedback.setWeaknesses(dto.getWeaknesses());
        feedback.setComments(dto.getComments());
        feedback.setRecommendation(recommendation);
        feedback.setProjectFit(dto.getProjectFit());
        feedback.setSuspectedCheating(dto.getSuspectedCheating());
        feedback = feedbackRepo.save(feedback);

        audit("FEEDBACK", feedbackId, "FEEDBACK_UPDATED", caller,
              "Feedback updated for round " + feedback.getRound().getRoundNumber());
        return toFeedbackDTO(feedback);
    }

    @Transactional(readOnly = true)
    public List<InterviewFeedbackDTO> getFeedbackForRound(Long roundId) {
        return feedbackRepo.findByRoundId(roundId)
                .stream().map(this::toFeedbackDTO).collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Stats & Audit
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        long total    = candidateRepo.count();
        long pending  = candidateRepo.countByStatus(CandidateStatus.PENDING);
        long inProg   = candidateRepo.countByStatus(CandidateStatus.IN_PROGRESS);
        long selected = candidateRepo.countByStatus(CandidateStatus.SELECTED);
        long rejected = candidateRepo.countByStatus(CandidateStatus.REJECTED);
        long onHold   = candidateRepo.countByStatus(CandidateStatus.ON_HOLD);

        stats.put("total",     total);
        stats.put("pending",   pending);
        stats.put("inProgress", inProg);
        stats.put("selected",  selected);
        stats.put("rejected",  rejected);
        stats.put("onHold",    onHold);
        stats.put("selectionRate", total > 0 ? Math.round((double) selected / total * 100.0) : 0);
        stats.put("rejectionRate", total > 0 ? Math.round((double) rejected / total * 100.0) : 0);

        long totalRounds     = roundRepo.count();
        long completedRounds = roundRepo.countByStatus(RoundStatus.COMPLETED);
        long scheduledRounds = roundRepo.countByStatus(RoundStatus.SCHEDULED);
        stats.put("totalRounds",     totalRounds);
        stats.put("completedRounds", completedRounds);
        stats.put("scheduledRounds", scheduledRounds);

        Double avgRating = feedbackRepo.getAverageOverallRating();
        stats.put("averageRating", avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 0);

        return stats;
    }

    @Transactional(readOnly = true)
    public InterviewRoundDTO getRound(Long roundId) {
        return toRoundDTO(findRound(roundId), true);
    }

    @Transactional(readOnly = true)
    public List<InterviewAuditLogDTO> getAuditLogs() {
        return auditRepo.findAllByOrderByCreatedAtDesc()
                .stream().map(this::toAuditDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InterviewAuditLogDTO> getAuditLogsForCandidate(Long candidateId) {
        return auditRepo.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("CANDIDATE", candidateId)
                .stream().map(this::toAuditDTO).collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private EmployeeDetails getEmployeeByEmail(String email) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
        return employeeRepo.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found for user: " + email));
    }

    private java.util.Optional<EmployeeDetails> findEmployeeByEmailOptional(String email) {
        return userRepo.findByEmail(email)
                .flatMap(user -> employeeRepo.findByUserId(user.getId()));
    }

    private InterviewCandidate findCandidate(Long id) {
        return candidateRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found: " + id));
    }

    private InterviewRound findRound(Long id) {
        return roundRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Interview round not found: " + id));
    }

    private void audit(String entityType, Long entityId, String action, EmployeeDetails performer, String details) {
        try {
            auditRepo.save(InterviewAuditLog.builder()
                    .entityType(entityType)
                    .entityId(entityId)
                    .action(action)
                    .performedBy(performer)
                    .details(details)
                    .build());
        } catch (Exception e) {
            log.error("Failed to save audit log: {}", e.getMessage());
        }
    }

    private void sendDecisionNotification(InterviewCandidate candidate, String decision, EmployeeDetails decidedBy) {
        try {
            List<String> emails = new ArrayList<>();
            // Collect all interviewers who participated
            List<InterviewRound> rounds = roundRepo.findByCandidateIdOrderByRoundNumberAsc(candidate.getId());
            rounds.stream()
                  .filter(r -> r.getInterviewer() != null && r.getInterviewer().getUser() != null)
                  .map(r -> r.getInterviewer().getUser().getEmail())
                  .filter(Objects::nonNull)
                  .forEach(emails::add);
            // Add creator if different
            if (candidate.getCreatedBy() != null && candidate.getCreatedBy().getUser() != null) {
                emails.add(candidate.getCreatedBy().getUser().getEmail());
            }
            // Add HR/Admin via findByRoleIn
            userRepo.findByRoleIn(List.of(Role.ADMIN, Role.DIRECTOR, Role.HR)).stream()
                    .map(User::getEmail).filter(Objects::nonNull).forEach(emails::add);

            String[] toArr = emails.stream().distinct().toArray(String[]::new);
            if (toArr.length > 0) {
                emailService.sendCandidateDecisionEmail(
                        toArr,
                        candidate.getName(),
                        candidate.getPosition(),
                        decision,
                        decidedBy.getFirstName() + " " + decidedBy.getLastName()
                );
            }
        } catch (Exception e) {
            log.error("Failed to send decision notification: {}", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DTO mapping
    // ─────────────────────────────────────────────────────────────────────────

    private InterviewCandidateDTO toCandidateDTO(InterviewCandidate c, List<InterviewRound> rounds, Map<Long, List<InterviewFeedback>> feedbacksByRound) {
        double avgRating = rounds.stream()
                .flatMap(r -> feedbacksByRound.getOrDefault(r.getId(), Collections.emptyList()).stream())
                .filter(f -> f.getOverallRating() != null)
                .mapToInt(InterviewFeedback::getOverallRating)
                .average().orElse(0);

        InterviewRound latest = rounds.isEmpty() ? null : rounds.get(rounds.size() - 1);

        return InterviewCandidateDTO.builder()
                .id(c.getId())
                .name(c.getName())
                .email(c.getEmail())
                .phone(c.getPhone())
                .position(c.getPosition())
                .department(c.getDepartment())
                .source(c.getSource())
                .resumePath(c.getResumePath())
                .notes(c.getNotes())
                .status(c.getStatus() != null ? c.getStatus().name() : null)
                .createdById(c.getCreatedBy() != null ? c.getCreatedBy().getId() : null)
                .createdByName(c.getCreatedBy() != null
                        ? c.getCreatedBy().getFirstName() + " " + c.getCreatedBy().getLastName() : null)
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .rounds(rounds.stream()
                        .map(r -> toRoundDTO(r, feedbacksByRound.getOrDefault(r.getId(), Collections.emptyList())))
                        .collect(Collectors.toList()))
                .totalRounds(rounds.size())
                .completedRounds((int) rounds.stream().filter(r -> r.getStatus() == RoundStatus.COMPLETED).count())
                .averageRating(avgRating > 0 ? Math.round(avgRating * 10.0) / 10.0 : null)
                .currentRoundType(latest != null && latest.getRoundType() != null ? latest.getRoundType().name() : null)
                .currentRoundStatus(latest != null && latest.getStatus() != null ? latest.getStatus().name() : null)
                .currentRoundInterviewerName(latest != null && latest.getInterviewer() != null
                        ? latest.getInterviewer().getFirstName() + " " + latest.getInterviewer().getLastName() : null)
                .currentRoundScheduledAt(latest != null ? latest.getScheduledAt() : null)
                .build();
    }

    private InterviewCandidateDTO toCandidateDTO(InterviewCandidate c, boolean includeRounds) {
        List<InterviewRound> rounds = includeRounds
                ? roundRepo.findByCandidateIdOrderByRoundNumberAsc(c.getId())
                : Collections.emptyList();

        double avgRating = rounds.stream()
                .flatMap(r -> feedbackRepo.findByRoundId(r.getId()).stream())
                .filter(f -> f.getOverallRating() != null)
                .mapToInt(InterviewFeedback::getOverallRating)
                .average().orElse(0);

        InterviewRound latest = rounds.isEmpty() ? null : rounds.get(rounds.size() - 1);

        return InterviewCandidateDTO.builder()
                .id(c.getId())
                .name(c.getName())
                .email(c.getEmail())
                .phone(c.getPhone())
                .position(c.getPosition())
                .department(c.getDepartment())
                .source(c.getSource())
                .resumePath(c.getResumePath())
                .notes(c.getNotes())
                .status(c.getStatus() != null ? c.getStatus().name() : null)
                .createdById(c.getCreatedBy() != null ? c.getCreatedBy().getId() : null)
                .createdByName(c.getCreatedBy() != null
                        ? c.getCreatedBy().getFirstName() + " " + c.getCreatedBy().getLastName() : null)
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .rounds(includeRounds ? rounds.stream().map(r -> toRoundDTO(r, true)).collect(Collectors.toList()) : null)
                .totalRounds(rounds.size())
                .completedRounds((int) rounds.stream().filter(r -> r.getStatus() == RoundStatus.COMPLETED).count())
                .averageRating(avgRating > 0 ? Math.round(avgRating * 10.0) / 10.0 : null)
                .currentRoundType(latest != null && latest.getRoundType() != null ? latest.getRoundType().name() : null)
                .currentRoundStatus(latest != null && latest.getStatus() != null ? latest.getStatus().name() : null)
                .currentRoundInterviewerName(latest != null && latest.getInterviewer() != null
                        ? latest.getInterviewer().getFirstName() + " " + latest.getInterviewer().getLastName() : null)
                .currentRoundScheduledAt(latest != null ? latest.getScheduledAt() : null)
                .build();
    }

    private InterviewRoundDTO toRoundDTO(InterviewRound r, boolean includeFeedback) {
        List<InterviewFeedback> feedbacks = includeFeedback
                ? feedbackRepo.findByRoundId(r.getId())
                : Collections.emptyList();
        return toRoundDTO(r, feedbacks);
    }

    private InterviewRoundDTO toRoundDTO(InterviewRound r, List<InterviewFeedback> feedbacks) {
        boolean feedbackSubmitted = !feedbacks.isEmpty();
        double avgRating = feedbacks.stream()
                .filter(f -> f.getOverallRating() != null)
                .mapToInt(InterviewFeedback::getOverallRating)
                .average().orElse(0);

        return InterviewRoundDTO.builder()
                .id(r.getId())
                .candidateId(r.getCandidate() != null ? r.getCandidate().getId() : null)
                .candidateName(r.getCandidate() != null ? r.getCandidate().getName() : null)
                .candidatePosition(r.getCandidate() != null ? r.getCandidate().getPosition() : null)
                .roundNumber(r.getRoundNumber())
                .roundType(r.getRoundType() != null ? r.getRoundType().name() : null)
                .scheduledAt(r.getScheduledAt())
                .durationMinutes(r.getDurationMinutes())
                .location(r.getLocation())
                .status(r.getStatus() != null ? r.getStatus().name() : null)
                .interviewerId(r.getInterviewer() != null ? r.getInterviewer().getId() : null)
                .interviewerName(r.getInterviewer() != null
                        ? r.getInterviewer().getFirstName() + " " + r.getInterviewer().getLastName() : null)
                .interviewerCode(r.getInterviewer() != null ? r.getInterviewer().getEmployeeCode() : null)
                .interviewerEmail(r.getInterviewer() != null && r.getInterviewer().getUser() != null
                        ? r.getInterviewer().getUser().getEmail() : null)
                .assignedById(r.getAssignedBy() != null ? r.getAssignedBy().getId() : null)
                .assignedByName(r.getAssignedBy() != null
                        ? r.getAssignedBy().getFirstName() + " " + r.getAssignedBy().getLastName() : null)
                .managerNotes(r.getManagerNotes())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .feedbacks(feedbacks.stream().map(this::toFeedbackDTO).collect(Collectors.toList()))
                .feedbackSubmitted(feedbackSubmitted)
                .averageRating(avgRating > 0 ? Math.round(avgRating * 10.0) / 10.0 : null)
                .build();
    }

    private InterviewFeedbackDTO toFeedbackDTO(InterviewFeedback f) {
        return InterviewFeedbackDTO.builder()
                .id(f.getId())
                .roundId(f.getRound() != null ? f.getRound().getId() : null)
                .submittedById(f.getSubmittedBy() != null ? f.getSubmittedBy().getId() : null)
                .submittedByName(f.getSubmittedBy() != null
                        ? f.getSubmittedBy().getFirstName() + " " + f.getSubmittedBy().getLastName() : null)
                .overallRating(f.getOverallRating())
                .technicalRating(f.getTechnicalRating())
                .communicationRating(f.getCommunicationRating())
                .problemSolvingRating(f.getProblemSolvingRating())
                .cultureFitRating(f.getCultureFitRating())
                .strengths(f.getStrengths())
                .weaknesses(f.getWeaknesses())
                .comments(f.getComments())
                .recommendation(f.getRecommendation() != null ? f.getRecommendation().name() : null)
                .projectFit(f.getProjectFit())
                .suspectedCheating(f.getSuspectedCheating())
                .submittedAt(f.getSubmittedAt())
                .build();
    }

    private InterviewAuditLogDTO toAuditDTO(InterviewAuditLog log) {
        return InterviewAuditLogDTO.builder()
                .id(log.getId())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .action(log.getAction())
                .performedById(log.getPerformedBy() != null ? log.getPerformedBy().getId() : null)
                .performedByName(log.getPerformedBy() != null
                        ? log.getPerformedBy().getFirstName() + " " + log.getPerformedBy().getLastName() : null)
                .details(log.getDetails())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
