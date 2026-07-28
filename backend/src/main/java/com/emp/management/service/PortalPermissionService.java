package com.emp.management.service;

import com.emp.management.entity.AtsFinalInterviewStatus;
import com.emp.management.entity.AtsTechInterviewStatus;
import com.emp.management.entity.EmployeeDetails;
import com.emp.management.entity.PortalPermission;
import com.emp.management.entity.Role;
import com.emp.management.entity.RoundStatus;
import com.emp.management.entity.User;
import com.emp.management.repository.AtsFinalRoundRepository;
import com.emp.management.repository.AtsTechnicalInterviewRepository;
import com.emp.management.repository.InterviewRoundRepository;
import com.emp.management.repository.PortalPermissionRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class PortalPermissionService {

    private final PortalPermissionRepository repository;
    private final InterviewRoundRepository interviewRoundRepository;
    private final AtsTechnicalInterviewRepository atsTechnicalInterviewRepository;
    private final AtsFinalRoundRepository atsFinalRoundRepository;

    // Round states that represent a still-pending obligation for the interviewer.
    private static final List<RoundStatus> ACTIVE_ROUND_STATUSES =
        List.of(RoundStatus.SCHEDULED, RoundStatus.IN_PROGRESS, RoundStatus.RESCHEDULED);

    // Canonical portal ids — mirrors the frontend's `portalRegistry` (src/components/Layout/portals/index.js).
    private static final List<String> PORTAL_IDS = List.of("hr", "training", "tandem", "interview");

    // First-run defaults — preserves the access every role already had before portal-level gating existed.
    // Tandem carries the Performance module, previously under Training, so it gets the same open access.
    private static final Map<Role, Set<String>> DEFAULTS = Map.of(
        Role.ADMIN,             Set.of("hr", "training", "tandem", "interview"),
        Role.DIRECTOR,          Set.of("hr", "training", "tandem", "interview"),
        Role.HR,                Set.of("hr", "training", "tandem", "interview"),
        Role.MANAGER,           Set.of("hr", "training", "tandem", "interview"),
        Role.ASSISTANT_MANAGER, Set.of("hr", "training", "tandem", "interview"),
        Role.EMPLOYEE,          Set.of("hr", "training", "tandem", "interview")
    );

    /** Idempotent — only inserts rows that don't exist yet, so admin edits survive restarts. */
    @PostConstruct
    @Transactional
    public void seedDefaults() {
        for (Role role : Role.values()) {
            Set<String> defaultAllowed = DEFAULTS.getOrDefault(role, Set.of());
            for (String portalId : PORTAL_IDS) {
                if (repository.findByRoleAndPortalId(role, portalId).isEmpty()) {
                    repository.save(PortalPermission.builder()
                        .role(role)
                        .portalId(portalId)
                        .enabled(defaultAllowed.contains(portalId))
                        .build());
                }
            }
        }
    }

    public List<String> getAllowedPortals(Role role) {
        return repository.findByRole(role).stream()
            .filter(PortalPermission::isEnabled)
            .map(PortalPermission::getPortalId)
            .toList();
    }

    public boolean isPortalAllowed(Role role, String portalId) {
        return repository.findByRoleAndPortalId(role, portalId)
            .map(PortalPermission::isEnabled)
            .orElse(false);
    }

    /**
     * Portals allowed for this specific user: their role's grants, plus any portal they need
     * to act on an assignment they currently hold — regardless of whether their role has that
     * portal enabled. Today that covers the Interview portal, checked across all three places
     * an interview can be assigned to someone: the classic {@code InterviewRound} flow, and the
     * ATS technical/final round flows (separate tables — HR's "assign interviewer" screening
     * decision writes to {@code ats_technical_interviews}, not {@code interview_rounds}).
     * Extend {@code hasActiveTaskAssignment} for other assignment types.
     */
    public List<String> getEffectiveAllowedPortals(User user) {
        Set<String> allowed = new LinkedHashSet<>(getAllowedPortals(user.getRole()));
        for (String portalId : PORTAL_IDS) {
            if (hasActiveTaskAssignment(user, portalId)) allowed.add(portalId);
        }
        return List.copyOf(allowed);
    }

    public boolean isPortalAllowedForUser(User user, String portalId) {
        return isPortalAllowed(user.getRole(), portalId) || hasActiveTaskAssignment(user, portalId);
    }

    private boolean hasActiveTaskAssignment(User user, String portalId) {
        if (!"interview".equals(portalId)) return false;
        EmployeeDetails emp = user.getEmployeeDetails();
        if (emp == null) return false;
        Long empId = emp.getId();
        return interviewRoundRepository.existsByInterviewerIdAndStatusIn(empId, ACTIVE_ROUND_STATUSES)
            || atsTechnicalInterviewRepository.existsByInterviewerIdAndInterviewStatusNot(empId, AtsTechInterviewStatus.EVALUATED)
            || atsFinalRoundRepository.existsByConductedByIdAndInterviewStatusNot(empId, AtsFinalInterviewStatus.EVALUATED);
    }

    public Map<String, List<String>> getAllRolePermissions() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Role role : Role.values()) {
            result.put(role.name(), getAllowedPortals(role));
        }
        return result;
    }

    @Transactional
    public void updateRolePermissions(Role role, List<String> allowedPortalIds) {
        Set<String> allowedSet = new HashSet<>(allowedPortalIds);
        for (String portalId : PORTAL_IDS) {
            PortalPermission perm = repository.findByRoleAndPortalId(role, portalId)
                .orElseGet(() -> PortalPermission.builder().role(role).portalId(portalId).build());
            perm.setEnabled(allowedSet.contains(portalId));
            repository.save(perm);
        }
    }
}
