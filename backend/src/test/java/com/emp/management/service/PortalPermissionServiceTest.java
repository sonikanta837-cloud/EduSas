package com.emp.management.service;

import com.emp.management.entity.*;
import com.emp.management.repository.AtsFinalRoundRepository;
import com.emp.management.repository.AtsTechnicalInterviewRepository;
import com.emp.management.repository.InterviewRoundRepository;
import com.emp.management.repository.PortalPermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * A user's effective portal access = their role's grants PLUS the "interview"
 * portal if they hold any still-pending assignment across THREE separate
 * interview systems (legacy InterviewRound, ATS technical, ATS final round) —
 * regardless of whether their role would normally see that portal at all.
 */
@ExtendWith(MockitoExtension.class)
class PortalPermissionServiceTest {

    @Mock private PortalPermissionRepository repository;
    @Mock private InterviewRoundRepository interviewRoundRepository;
    @Mock private AtsTechnicalInterviewRepository atsTechnicalInterviewRepository;
    @Mock private AtsFinalRoundRepository atsFinalRoundRepository;

    @InjectMocks private PortalPermissionService portalPermissionService;

    private User user;
    private EmployeeDetails employee;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).email("emp@company.com").role(Role.EMPLOYEE).active(true).build();
        employee = EmployeeDetails.builder().id(5L).user(user).active(true).build();
        user.setEmployeeDetails(employee);
    }

    // ── getAllowedPortals ─────────────────────────────────────────────────────

    @Test
    void getAllowedPortals_filtersOutDisabledPortals() {
        when(repository.findByRole(Role.EMPLOYEE)).thenReturn(List.of(
                PortalPermission.builder().role(Role.EMPLOYEE).portalId("hr").enabled(true).build(),
                PortalPermission.builder().role(Role.EMPLOYEE).portalId("interview").enabled(false).build()
        ));

        List<String> result = portalPermissionService.getAllowedPortals(Role.EMPLOYEE);

        assertThat(result).containsExactly("hr");
    }

    // ── isPortalAllowed ───────────────────────────────────────────────────────

    @Test
    void isPortalAllowed_enabledPermission_returnsTrue() {
        when(repository.findByRoleAndPortalId(Role.EMPLOYEE, "hr"))
                .thenReturn(Optional.of(PortalPermission.builder().enabled(true).build()));

        assertThat(portalPermissionService.isPortalAllowed(Role.EMPLOYEE, "hr")).isTrue();
    }

    @Test
    void isPortalAllowed_disabledPermission_returnsFalse() {
        when(repository.findByRoleAndPortalId(Role.EMPLOYEE, "interview"))
                .thenReturn(Optional.of(PortalPermission.builder().enabled(false).build()));

        assertThat(portalPermissionService.isPortalAllowed(Role.EMPLOYEE, "interview")).isFalse();
    }

    @Test
    void isPortalAllowed_noRowConfigured_returnsFalse() {
        when(repository.findByRoleAndPortalId(Role.EMPLOYEE, "unknown")).thenReturn(Optional.empty());

        assertThat(portalPermissionService.isPortalAllowed(Role.EMPLOYEE, "unknown")).isFalse();
    }

    // ── getEffectiveAllowedPortals — role grant only ─────────────────────────

    @Test
    void getEffectiveAllowedPortals_roleAlreadyGrantsPortal_noAssignmentCheckNeeded() {
        when(repository.findByRole(Role.EMPLOYEE)).thenReturn(List.of(
                PortalPermission.builder().portalId("hr").enabled(true).build(),
                PortalPermission.builder().portalId("interview").enabled(true).build()
        ));
        when(interviewRoundRepository.existsByInterviewerIdAndStatusIn(anyLong(), anyList())).thenReturn(false);
        when(atsTechnicalInterviewRepository.existsByInterviewerIdAndInterviewStatusNot(anyLong(), any())).thenReturn(false);
        when(atsFinalRoundRepository.existsByConductedByIdAndInterviewStatusNot(anyLong(), any())).thenReturn(false);

        List<String> result = portalPermissionService.getEffectiveAllowedPortals(user);

        assertThat(result).contains("hr", "interview");
    }

    // ── getEffectiveAllowedPortals — assignment-based grant, role denies ────

    @Test
    void getEffectiveAllowedPortals_roleDeniesInterview_butHasActiveLegacyRoundAssignment_stillGranted() {
        when(repository.findByRole(Role.EMPLOYEE)).thenReturn(List.of(
                PortalPermission.builder().portalId("hr").enabled(true).build(),
                PortalPermission.builder().portalId("interview").enabled(false).build()
        ));
        when(interviewRoundRepository.existsByInterviewerIdAndStatusIn(eq(5L), anyList())).thenReturn(true);

        List<String> result = portalPermissionService.getEffectiveAllowedPortals(user);

        assertThat(result).contains("interview");
    }

    @Test
    void getEffectiveAllowedPortals_roleDeniesInterview_butHasActiveAtsTechnicalAssignment_stillGranted() {
        when(repository.findByRole(Role.EMPLOYEE)).thenReturn(List.of(
                PortalPermission.builder().portalId("interview").enabled(false).build()
        ));
        when(interviewRoundRepository.existsByInterviewerIdAndStatusIn(anyLong(), anyList())).thenReturn(false);
        when(atsTechnicalInterviewRepository.existsByInterviewerIdAndInterviewStatusNot(eq(5L), eq(AtsTechInterviewStatus.EVALUATED)))
                .thenReturn(true);

        List<String> result = portalPermissionService.getEffectiveAllowedPortals(user);

        assertThat(result).contains("interview");
    }

    @Test
    void getEffectiveAllowedPortals_roleDeniesInterview_butHasActiveAtsFinalRoundAssignment_stillGranted() {
        when(repository.findByRole(Role.EMPLOYEE)).thenReturn(List.of(
                PortalPermission.builder().portalId("interview").enabled(false).build()
        ));
        when(interviewRoundRepository.existsByInterviewerIdAndStatusIn(anyLong(), anyList())).thenReturn(false);
        when(atsTechnicalInterviewRepository.existsByInterviewerIdAndInterviewStatusNot(anyLong(), any())).thenReturn(false);
        when(atsFinalRoundRepository.existsByConductedByIdAndInterviewStatusNot(eq(5L), eq(AtsFinalInterviewStatus.EVALUATED)))
                .thenReturn(true);

        List<String> result = portalPermissionService.getEffectiveAllowedPortals(user);

        assertThat(result).contains("interview");
    }

    @Test
    void getEffectiveAllowedPortals_noActiveAssignmentAnywhere_interviewNotGranted() {
        when(repository.findByRole(Role.EMPLOYEE)).thenReturn(List.of(
                PortalPermission.builder().portalId("interview").enabled(false).build()
        ));
        when(interviewRoundRepository.existsByInterviewerIdAndStatusIn(anyLong(), anyList())).thenReturn(false);
        when(atsTechnicalInterviewRepository.existsByInterviewerIdAndInterviewStatusNot(anyLong(), any())).thenReturn(false);
        when(atsFinalRoundRepository.existsByConductedByIdAndInterviewStatusNot(anyLong(), any())).thenReturn(false);

        List<String> result = portalPermissionService.getEffectiveAllowedPortals(user);

        assertThat(result).doesNotContain("interview");
    }

    @Test
    void getEffectiveAllowedPortals_userWithNoEmployeeDetails_skipsAssignmentCheckSafely() {
        User orphanUser = User.builder().id(2L).email("orphan@company.com").role(Role.EMPLOYEE).active(true).build();
        // employeeDetails intentionally left null
        when(repository.findByRole(Role.EMPLOYEE)).thenReturn(List.of(
                PortalPermission.builder().portalId("interview").enabled(false).build()
        ));

        List<String> result = portalPermissionService.getEffectiveAllowedPortals(orphanUser);

        assertThat(result).doesNotContain("interview");
        verify(interviewRoundRepository, never()).existsByInterviewerIdAndStatusIn(any(), any());
    }

    // ── isPortalAllowedForUser ────────────────────────────────────────────────

    @Test
    void isPortalAllowedForUser_deniedByRole_butAllowedByActiveAssignment_returnsTrue() {
        when(repository.findByRoleAndPortalId(Role.EMPLOYEE, "interview"))
                .thenReturn(Optional.of(PortalPermission.builder().enabled(false).build()));
        when(interviewRoundRepository.existsByInterviewerIdAndStatusIn(eq(5L), anyList())).thenReturn(true);

        assertThat(portalPermissionService.isPortalAllowedForUser(user, "interview")).isTrue();
    }

    @Test
    void isPortalAllowedForUser_deniedEverywhere_returnsFalse() {
        when(repository.findByRoleAndPortalId(Role.EMPLOYEE, "interview")).thenReturn(Optional.empty());
        when(interviewRoundRepository.existsByInterviewerIdAndStatusIn(anyLong(), anyList())).thenReturn(false);
        when(atsTechnicalInterviewRepository.existsByInterviewerIdAndInterviewStatusNot(anyLong(), any())).thenReturn(false);
        when(atsFinalRoundRepository.existsByConductedByIdAndInterviewStatusNot(anyLong(), any())).thenReturn(false);

        assertThat(portalPermissionService.isPortalAllowedForUser(user, "interview")).isFalse();
    }

    @Test
    void isPortalAllowedForUser_nonInterviewPortal_ignoresAssignmentCheckEntirely() {
        when(repository.findByRoleAndPortalId(Role.EMPLOYEE, "hr")).thenReturn(Optional.empty());

        assertThat(portalPermissionService.isPortalAllowedForUser(user, "hr")).isFalse();
        verifyNoInteractions(interviewRoundRepository, atsTechnicalInterviewRepository, atsFinalRoundRepository);
    }

    // ── admin role management ─────────────────────────────────────────────────

    @Test
    void getAllRolePermissions_returnsEntryForEveryRole() {
        for (Role role : Role.values()) {
            when(repository.findByRole(role)).thenReturn(List.of());
        }

        Map<String, List<String>> result = portalPermissionService.getAllRolePermissions();

        assertThat(result).containsKeys("ADMIN", "DIRECTOR", "HR", "MANAGER", "ASSISTANT_MANAGER", "EMPLOYEE");
    }

    @Test
    void updateRolePermissions_updatesExistingRow() {
        PortalPermission existing = PortalPermission.builder().id(1L).role(Role.MANAGER).portalId("hr").enabled(false).build();
        when(repository.findByRoleAndPortalId(Role.MANAGER, "hr")).thenReturn(Optional.of(existing));
        when(repository.findByRoleAndPortalId(eq(Role.MANAGER), argThat(id -> !"hr".equals(id))))
                .thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        portalPermissionService.updateRolePermissions(Role.MANAGER, List.of("hr"));

        assertThat(existing.isEnabled()).isTrue();
        verify(repository, times(4)).save(any()); // one row per PORTAL_IDS entry
    }

    @Test
    void updateRolePermissions_createsMissingRowForNewlyGrantedPortal() {
        when(repository.findByRoleAndPortalId(eq(Role.MANAGER), anyString())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        portalPermissionService.updateRolePermissions(Role.MANAGER, List.of("training"));

        verify(repository).save(argThat(p -> "training".equals(p.getPortalId()) && p.isEnabled()));
    }
}
