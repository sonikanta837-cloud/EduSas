package com.emp.management.service;

import com.emp.management.dto.*;
import com.emp.management.entity.*;
import com.emp.management.exception.BadRequestException;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PipService {

    private final PipRepository              pipRepository;
    private final PipGoalRepository          goalRepository;
    private final PipWeeklyReviewRepository  reviewRepository;
    private final PipCommentRepository       commentRepository;
    private final PipAuditLogRepository      auditRepository;
    private final EmployeeDetailsRepository  employeeDetailsRepository;
    private final UserRepository             userRepository;
    private final EmailService               emailService;
    private final PipNotificationService     pipNotificationService;

    // ── helpers ───────────────────────────────────────────────────────────────

    private EmployeeDetails requireEmployee(String email) {
        return employeeDetailsRepository.findByUserEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + email));
    }

    private PerformanceImprovementPlan requirePip(Long id) {
        return pipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PIP not found: " + id));
    }

    private void audit(PerformanceImprovementPlan pip, EmployeeDetails actor, String action, String details) {
        auditRepository.save(PipAuditLog.builder()
                .pip(pip).performedBy(actor).action(action).details(details).build());
    }

    // ── DTO converters ────────────────────────────────────────────────────────

    private PipGoalDTO toGoalDTO(PipGoal g) {
        return PipGoalDTO.builder()
                .id(g.getId()).pipId(g.getPip().getId())
                .title(g.getTitle()).description(g.getDescription())
                .successCriteria(g.getSuccessCriteria())
                .progressPercent(g.getProgressPercent())
                .status(g.getStatus().name())
                .targetDate(g.getTargetDate())
                .createdAt(g.getCreatedAt()).updatedAt(g.getUpdatedAt())
                .build();
    }

    private PipWeeklyReviewDTO toReviewDTO(PipWeeklyReview r) {
        return PipWeeklyReviewDTO.builder()
                .id(r.getId()).pipId(r.getPip().getId())
                .conductedById(r.getConductedBy() != null ? r.getConductedBy().getId() : null)
                .conductedByName(r.getConductedBy() != null ? r.getConductedBy().getFullName() : null)
                .weekNumber(r.getWeekNumber()).reviewDate(r.getReviewDate())
                .overallProgress(r.getOverallProgress()).progressRating(r.getProgressRating())
                .achievements(r.getAchievements()).challenges(r.getChallenges())
                .actionItems(r.getActionItems()).createdAt(r.getCreatedAt())
                .build();
    }

    private PipCommentDTO toCommentDTO(PipComment c) {
        return PipCommentDTO.builder()
                .id(c.getId()).pipId(c.getPip().getId())
                .authorId(c.getAuthor().getId())
                .authorName(c.getAuthor().getFullName())
                .authorRole(c.getAuthor().getUser() != null ? c.getAuthor().getUser().getRole().name() : null)
                .content(c.getContent()).createdAt(c.getCreatedAt())
                .build();
    }

    private PipAuditLogDTO toAuditDTO(PipAuditLog a) {
        return PipAuditLogDTO.builder()
                .id(a.getId()).pipId(a.getPip().getId())
                .performedById(a.getPerformedBy() != null ? a.getPerformedBy().getId() : null)
                .performedByName(a.getPerformedBy() != null ? a.getPerformedBy().getFullName() : null)
                .action(a.getAction()).details(a.getDetails()).createdAt(a.getCreatedAt())
                .build();
    }

    private PipDTO toListDTO(PerformanceImprovementPlan pip) {
        long totalGoals    = pipRepository.countTotalGoals(pip.getId());
        long achievedGoals = pipRepository.countAchievedGoals(pip.getId());
        long reviewCount   = pipRepository.countWeeklyReviews(pip.getId());
        long commentCount  = pipRepository.countComments(pip.getId());
        Double avgProgress = pipRepository.avgGoalProgress(pip.getId());
        boolean overdue    = pip.getStatus() == PipStatus.ACTIVE && pip.getEndDate().isBefore(LocalDate.now());

        return PipDTO.builder()
                .id(pip.getId())
                .employeeId(pip.getEmployee().getId())
                .employeeName(pip.getEmployee().getFullName())
                .employeeDepartment(pip.getEmployee().getDepartment())
                .employeePosition(pip.getEmployee().getPosition())
                .createdById(pip.getCreatedBy().getId())
                .createdByName(pip.getCreatedBy().getFullName())
                .title(pip.getTitle())
                .startDate(pip.getStartDate()).endDate(pip.getEndDate())
                .reason(pip.getReason()).improvementAreas(pip.getImprovementAreas())
                .supportProvided(pip.getSupportProvided()).consequences(pip.getConsequences())
                .status(pip.getStatus().name())
                .finalNotes(pip.getFinalNotes()).outcomeDate(pip.getOutcomeDate())
                .createdAt(pip.getCreatedAt()).updatedAt(pip.getUpdatedAt())
                .totalGoals((int) totalGoals).achievedGoals((int) achievedGoals)
                .overallProgressPercent(avgProgress != null ? (int) Math.round(avgProgress) : 0)
                .weeklyReviewCount((int) reviewCount).commentCount((int) commentCount)
                .overdue(overdue)
                .build();
    }

    private PipDTO toDetailDTO(PerformanceImprovementPlan pip) {
        PipDTO dto = toListDTO(pip);
        dto.setGoals(pip.getGoals().stream().map(this::toGoalDTO).collect(Collectors.toList()));
        dto.setWeeklyReviews(pip.getWeeklyReviews().stream().map(this::toReviewDTO).collect(Collectors.toList()));
        dto.setComments(pip.getComments().stream().map(this::toCommentDTO).collect(Collectors.toList()));
        dto.setAuditLogs(pip.getAuditLogs().stream().map(this::toAuditDTO).collect(Collectors.toList()));
        return dto;
    }

    // ── queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PipDTO> getPips(String userEmail) {
        EmployeeDetails me = requireEmployee(userEmail);
        Role role = me.getUser().getRole();

        List<PerformanceImprovementPlan> pips;
        if (role == Role.ADMIN || role == Role.DIRECTOR || role == Role.HR) {
            pips = pipRepository.findAllByOrderByCreatedAtDesc();
        } else if (role == Role.MANAGER || role == Role.ASSISTANT_MANAGER) {
            Set<Long> seen = new LinkedHashSet<>();
            List<PerformanceImprovementPlan> result = new ArrayList<>();
            // PIPs assigned to this manager themselves
            for (PerformanceImprovementPlan p : pipRepository.findByEmployeeIdOrderByCreatedAtDesc(me.getId())) {
                if (seen.add(p.getId())) result.add(p);
            }
            // PIPs for their subordinates
            for (PerformanceImprovementPlan p : pipRepository.findByEmployeeManagerId(me.getId())) {
                if (seen.add(p.getId())) result.add(p);
            }
            // PIPs created by this manager
            for (PerformanceImprovementPlan p : pipRepository.findByCreatedByIdOrderByCreatedAtDesc(me.getId())) {
                if (seen.add(p.getId())) result.add(p);
            }
            pips = result;
        } else {
            pips = pipRepository.findByEmployeeIdOrderByCreatedAtDesc(me.getId());
        }

        return pips.stream().map(this::toListDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PipDTO> getPipsByEmployee(Long empId, String userEmail) {
        EmployeeDetails me = requireEmployee(userEmail);
        Role role = me.getUser().getRole();

        if (role == Role.EMPLOYEE && !me.getId().equals(empId)) {
            throw new BadRequestException("Access denied");
        }
        return pipRepository.findByEmployeeIdOrderByCreatedAtDesc(empId)
                .stream().map(this::toListDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PipDTO getPipDetail(Long pipId, String userEmail) {
        PerformanceImprovementPlan pip = requirePip(pipId);
        EmployeeDetails me = requireEmployee(userEmail);
        Role role = me.getUser().getRole();

        // EMPLOYEE can only see their own PIP
        if (role == Role.EMPLOYEE && !pip.getEmployee().getId().equals(me.getId())) {
            throw new BadRequestException("Access denied");
        }
        return toDetailDTO(pip);
    }

    // ── create PIP ────────────────────────────────────────────────────────────

    @Transactional
    public PipDTO createPip(PipDTO dto, String creatorEmail) {
        EmployeeDetails creator  = requireEmployee(creatorEmail);
        EmployeeDetails employee = employeeDetailsRepository.findById(dto.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + dto.getEmployeeId()));

        if (dto.getStartDate() == null || dto.getEndDate() == null)
            throw new BadRequestException("Start date and end date are required");
        if (dto.getEndDate().isBefore(dto.getStartDate()))
            throw new BadRequestException("End date must be after start date");

        PerformanceImprovementPlan pip = PerformanceImprovementPlan.builder()
                .employee(employee).createdBy(creator)
                .title(dto.getTitle()).startDate(dto.getStartDate()).endDate(dto.getEndDate())
                .reason(dto.getReason()).improvementAreas(dto.getImprovementAreas())
                .supportProvided(dto.getSupportProvided()).consequences(dto.getConsequences())
                .status(PipStatus.ACTIVE)
                .build();
        pip = pipRepository.save(pip);

        // Save goals if provided
        if (dto.getGoals() != null) {
            for (PipGoalDTO gDto : dto.getGoals()) {
                PipGoal goal = PipGoal.builder()
                        .pip(pip).title(gDto.getTitle()).description(gDto.getDescription())
                        .successCriteria(gDto.getSuccessCriteria()).targetDate(gDto.getTargetDate())
                        .progressPercent(0).status(PipGoalStatus.NOT_STARTED)
                        .build();
                goalRepository.save(goal);
            }
        }

        audit(pip, creator, "PIP_CREATED",
                "PIP \"" + pip.getTitle() + "\" created for " + employee.getFullName() +
                " from " + pip.getStartDate() + " to " + pip.getEndDate());

        // Reload to get goals in collection
        pip = requirePip(pip.getId());

        // Email employee
        sendPipCreatedNotification(pip);

        log.info("PIP {} created by {} for {}", pip.getId(), creator.getFullName(), employee.getFullName());
        return toDetailDTO(pip);
    }

    // ── update PIP ────────────────────────────────────────────────────────────

    @Transactional
    public PipDTO updatePip(Long pipId, PipDTO dto, String userEmail) {
        PerformanceImprovementPlan pip = requirePip(pipId);
        EmployeeDetails actor = requireEmployee(userEmail);

        pip.setTitle(dto.getTitle());
        pip.setStartDate(dto.getStartDate());
        pip.setEndDate(dto.getEndDate());
        pip.setReason(dto.getReason());
        pip.setImprovementAreas(dto.getImprovementAreas());
        pip.setSupportProvided(dto.getSupportProvided());
        pip.setConsequences(dto.getConsequences());
        pip = pipRepository.save(pip);

        audit(pip, actor, "PIP_UPDATED", "PIP details updated by " + actor.getFullName());
        return toListDTO(pip);
    }

    // ── goals ─────────────────────────────────────────────────────────────────

    @Transactional
    public PipGoalDTO addGoal(Long pipId, PipGoalDTO dto, String userEmail) {
        PerformanceImprovementPlan pip = requirePip(pipId);
        EmployeeDetails actor = requireEmployee(userEmail);

        PipGoal goal = PipGoal.builder()
                .pip(pip).title(dto.getTitle()).description(dto.getDescription())
                .successCriteria(dto.getSuccessCriteria()).targetDate(dto.getTargetDate())
                .progressPercent(0).status(PipGoalStatus.NOT_STARTED)
                .build();
        goal = goalRepository.save(goal);

        audit(pip, actor, "GOAL_ADDED", "Goal added: \"" + goal.getTitle() + "\"");
        return toGoalDTO(goal);
    }

    @Transactional
    public PipGoalDTO updateGoal(Long pipId, Long goalId, PipGoalDTO dto, String userEmail) {
        PerformanceImprovementPlan pip = requirePip(pipId);
        PipGoal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new ResourceNotFoundException("Goal not found: " + goalId));
        if (!goal.getPip().getId().equals(pipId)) {
            throw new BadRequestException("Goal does not belong to the specified PIP");
        }
        EmployeeDetails actor = requireEmployee(userEmail);

        goal.setTitle(dto.getTitle());
        goal.setDescription(dto.getDescription());
        goal.setSuccessCriteria(dto.getSuccessCriteria());
        goal.setTargetDate(dto.getTargetDate());
        goal.setProgressPercent(Math.max(0, Math.min(100, dto.getProgressPercent())));
        goal.setStatus(PipGoalStatus.valueOf(dto.getStatus()));
        goal = goalRepository.save(goal);

        audit(pip, actor, "GOAL_UPDATED",
                "Goal \"" + goal.getTitle() + "\" updated — progress " + goal.getProgressPercent() + "%, status: " + goal.getStatus());
        return toGoalDTO(goal);
    }

    @Transactional
    public void deleteGoal(Long pipId, Long goalId, String userEmail) {
        PerformanceImprovementPlan pip = requirePip(pipId);
        PipGoal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new ResourceNotFoundException("Goal not found: " + goalId));
        if (!goal.getPip().getId().equals(pipId)) {
            throw new BadRequestException("Goal does not belong to the specified PIP");
        }
        EmployeeDetails actor = requireEmployee(userEmail);

        String title = goal.getTitle();
        goalRepository.delete(goal);
        audit(pip, actor, "GOAL_DELETED", "Goal deleted: \"" + title + "\"");
    }

    // ── weekly reviews ────────────────────────────────────────────────────────

    @Transactional
    public PipWeeklyReviewDTO addWeeklyReview(Long pipId, PipWeeklyReviewDTO dto, String userEmail) {
        PerformanceImprovementPlan pip = requirePip(pipId);
        EmployeeDetails actor = requireEmployee(userEmail);

        int nextWeek = (int) reviewRepository.countByPipId(pipId) + 1;

        PipWeeklyReview review = PipWeeklyReview.builder()
                .pip(pip).conductedBy(actor)
                .weekNumber(nextWeek)
                .reviewDate(dto.getReviewDate() != null ? dto.getReviewDate() : LocalDate.now())
                .overallProgress(dto.getOverallProgress())
                .progressRating(Math.max(1, Math.min(5, dto.getProgressRating())))
                .achievements(dto.getAchievements())
                .challenges(dto.getChallenges())
                .actionItems(dto.getActionItems())
                .build();
        review = reviewRepository.save(review);

        audit(pip, actor, "REVIEW_ADDED",
                "Week " + nextWeek + " review added by " + actor.getFullName() +
                " — progress rating: " + review.getProgressRating() + "/5");
        return toReviewDTO(review);
    }

    // ── comments ─────────────────────────────────────────────────────────────

    @Transactional
    public PipCommentDTO addComment(Long pipId, String content, String userEmail) {
        PerformanceImprovementPlan pip = requirePip(pipId);
        EmployeeDetails actor = requireEmployee(userEmail);

        if (content == null || content.isBlank())
            throw new BadRequestException("Comment content cannot be empty");

        PipComment comment = PipComment.builder()
                .pip(pip).author(actor).content(content.trim())
                .build();
        comment = commentRepository.save(comment);

        audit(pip, actor, "COMMENT_ADDED", actor.getFullName() + " added a comment");
        pipNotificationService.notifyComment(pip, actor, content.trim());
        return toCommentDTO(comment);
    }

    // ── outcome ───────────────────────────────────────────────────────────────

    @Transactional
    public PipDTO setOutcome(Long pipId, PipOutcomeDTO dto, String userEmail) {
        PerformanceImprovementPlan pip = requirePip(pipId);
        EmployeeDetails actor = requireEmployee(userEmail);

        PipStatus newStatus;
        try {
            newStatus = PipStatus.valueOf(dto.getStatus());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid outcome status: " + dto.getStatus());
        }
        if (newStatus == PipStatus.ACTIVE)
            throw new BadRequestException("Cannot set outcome status to ACTIVE");

        String prevStatus = pip.getStatus().name();
        pip.setStatus(newStatus);
        pip.setFinalNotes(dto.getFinalNotes());
        pip.setOutcomeDate(dto.getOutcomeDate() != null ? dto.getOutcomeDate() : LocalDate.now());

        if (newStatus == PipStatus.EXTENDED && dto.getNewEndDate() != null) {
            pip.setEndDate(dto.getNewEndDate());
            pip.setStatus(PipStatus.ACTIVE); // Extended = still active with new date
        }

        pip = pipRepository.save(pip);
        audit(pip, actor, "OUTCOME_SET",
                "Status changed from " + prevStatus + " to " + pip.getStatus() + " by " + actor.getFullName() +
                (dto.getFinalNotes() != null ? " — Notes: " + dto.getFinalNotes() : ""));

        // Send outcome email
        sendPipOutcomeNotification(pip, actor);
        return toListDTO(pip);
    }

    // ── delete PIP ────────────────────────────────────────────────────────────

    @Transactional
    public void deletePip(Long pipId, String userEmail) {
        PerformanceImprovementPlan pip = requirePip(pipId);
        EmployeeDetails actor = requireEmployee(userEmail);
        log.info("PIP {} deleted by {}", pipId, actor.getFullName());
        pipRepository.delete(pip);
    }

    // ── email helpers ─────────────────────────────────────────────────────────

    private void sendPipCreatedNotification(PerformanceImprovementPlan pip) {
        try {
            EmployeeDetails employee = pip.getEmployee();
            if (employee.getUser() == null || employee.getUser().getEmail() == null) return;

            List<String> goalTitles = pip.getGoals().stream()
                    .map(PipGoal::getTitle).collect(Collectors.toList());

            // CC: manager (if different from creator), admin, HR
            Set<String> ccSet = new LinkedHashSet<>();
            if (employee.getManager() != null && employee.getManager().getUser() != null &&
                    employee.getManager().getUser().getEmail() != null &&
                    !employee.getManager().getId().equals(pip.getCreatedBy().getId())) {
                ccSet.add(employee.getManager().getUser().getEmail());
            }
            userRepository.findByRoleIn(java.util.List.of(Role.ADMIN, Role.DIRECTOR)).stream()
                    .map(User::getEmail).filter(Objects::nonNull)
                    .filter(e -> !e.equals(employee.getUser().getEmail()))
                    .forEach(ccSet::add);

            emailService.sendPipCreatedEmail(
                    employee.getUser().getEmail(),
                    ccSet.isEmpty() ? null : ccSet.toArray(new String[0]),
                    employee.getFullName(),
                    pip.getCreatedBy().getFullName(),
                    pip.getTitle(),
                    pip.getStartDate(),
                    pip.getEndDate(),
                    pip.getReason(),
                    goalTitles);
        } catch (Exception e) {
            log.error("Failed to send PIP created email for PIP {}: {}", pip.getId(), e.getMessage());
        }
    }

    private void sendPipOutcomeNotification(PerformanceImprovementPlan pip, EmployeeDetails actor) {
        try {
            EmployeeDetails employee = pip.getEmployee();
            if (employee.getUser() == null || employee.getUser().getEmail() == null) return;

            Set<String> ccSet = new LinkedHashSet<>();
            if (employee.getManager() != null && employee.getManager().getUser() != null &&
                    employee.getManager().getUser().getEmail() != null) {
                ccSet.add(employee.getManager().getUser().getEmail());
            }
            userRepository.findByRoleIn(java.util.List.of(Role.ADMIN, Role.DIRECTOR)).stream()
                    .map(User::getEmail).filter(Objects::nonNull)
                    .filter(e -> !e.equals(employee.getUser().getEmail()))
                    .forEach(ccSet::add);

            emailService.sendPipOutcomeEmail(
                    employee.getUser().getEmail(),
                    ccSet.isEmpty() ? null : ccSet.toArray(new String[0]),
                    employee.getFullName(),
                    pip.getTitle(),
                    pip.getStatus().name(),
                    pip.getOutcomeDate(),
                    pip.getFinalNotes());
        } catch (Exception e) {
            log.error("Failed to send PIP outcome email for PIP {}: {}", pip.getId(), e.getMessage());
        }
    }
}
