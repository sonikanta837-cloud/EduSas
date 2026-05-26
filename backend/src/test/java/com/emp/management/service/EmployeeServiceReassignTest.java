package com.emp.management.service;

import com.emp.management.entity.EmployeeDetails;
import com.emp.management.entity.Role;
import com.emp.management.entity.User;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.EmployeeDetailsRepository;
import com.emp.management.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceReassignTest {

    @Mock private EmployeeDetailsRepository employeeDetailsRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private EmployeeService employeeService;

    private User adminUser;
    private EmployeeDetails adminEmployee;
    private User managerUser;
    private EmployeeDetails managerEmployee;
    private User reportingManagerUser;
    private EmployeeDetails reportingManager;
    private User subordinate1User;
    private EmployeeDetails subordinate1;
    private User subordinate2User;
    private EmployeeDetails subordinate2;

    @BeforeEach
    void setUp() {
        adminUser = User.builder().id(1L).email("admin@company.com").role(Role.ADMIN).active(true).build();
        adminEmployee = EmployeeDetails.builder().id(1L).user(adminUser)
                .firstName("Admin").lastName("User").employeeCode("10001").active(true).build();
        adminUser.setEmployeeDetails(adminEmployee);

        reportingManagerUser = User.builder().id(10L).email("boss@company.com").role(Role.ADMIN).active(true).build();
        reportingManager = EmployeeDetails.builder().id(10L).user(reportingManagerUser)
                .firstName("Senior").lastName("Boss").employeeCode("10010").active(true).build();
        reportingManagerUser.setEmployeeDetails(reportingManager);

        managerUser = User.builder().id(2L).email("mgr@company.com").role(Role.MANAGER).active(true).build();
        managerEmployee = EmployeeDetails.builder().id(2L).user(managerUser)
                .firstName("Manager").lastName("Smith").employeeCode("10002").active(true)
                .manager(reportingManager).build();
        managerUser.setEmployeeDetails(managerEmployee);

        subordinate1User = User.builder().id(3L).email("sub1@company.com").role(Role.EMPLOYEE).active(true).build();
        subordinate1 = EmployeeDetails.builder().id(3L).user(subordinate1User)
                .firstName("Sub").lastName("One").employeeCode("10003").active(true)
                .manager(managerEmployee).build();
        subordinate1User.setEmployeeDetails(subordinate1);

        subordinate2User = User.builder().id(4L).email("sub2@company.com").role(Role.EMPLOYEE).active(true).build();
        subordinate2 = EmployeeDetails.builder().id(4L).user(subordinate2User)
                .firstName("Sub").lastName("Two").employeeCode("10004").active(true)
                .manager(managerEmployee).build();
        subordinate2User.setEmployeeDetails(subordinate2);
    }

    // ── reassignSubordinates — reporting manager (active) preferred ───────────

    @Test
    void toggleStatus_deactivateManager_reassignSubordinatesToReportingManager() {
        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(managerEmployee));
        when(employeeDetailsRepository.findByManagerId(2L)).thenReturn(List.of(subordinate1, subordinate2));
        when(employeeDetailsRepository.save(any())).thenReturn(managerEmployee);

        employeeService.toggleEmployeeStatus(2L);

        // Both subordinates reassigned to reportingManager (active)
        assertThat(subordinate1.getManager()).isEqualTo(reportingManager);
        assertThat(subordinate2.getManager()).isEqualTo(reportingManager);
    }

    @Test
    void toggleStatus_deactivateManager_subordinatesSaved() {
        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(managerEmployee));
        when(employeeDetailsRepository.findByManagerId(2L)).thenReturn(List.of(subordinate1, subordinate2));
        when(employeeDetailsRepository.save(any())).thenReturn(managerEmployee);

        employeeService.toggleEmployeeStatus(2L);

        // save called for sub1, sub2, and the manager itself = 3 times minimum
        verify(employeeDetailsRepository, atLeast(3)).save(any());
    }

    // ── reassignSubordinates — fallback to first active ADMIN ─────────────────

    @Test
    void toggleStatus_deactivateManager_reportingManagerInactive_fallbackToAdmin() {
        // Make reporting manager inactive
        reportingManager.setActive(false);
        reportingManagerUser.setActive(false);

        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(managerEmployee));
        when(employeeDetailsRepository.findByManagerId(2L)).thenReturn(List.of(subordinate1));
        when(employeeDetailsRepository.findActiveAdmins()).thenReturn(List.of(adminEmployee));
        when(employeeDetailsRepository.save(any())).thenReturn(managerEmployee);

        employeeService.toggleEmployeeStatus(2L);

        assertThat(subordinate1.getManager()).isEqualTo(adminEmployee);
    }

    @Test
    void toggleStatus_deactivateManager_noReportingManager_fallbackToAdmin() {
        // Remove reporting manager
        managerEmployee.setManager(null);

        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(managerEmployee));
        when(employeeDetailsRepository.findByManagerId(2L)).thenReturn(List.of(subordinate1));
        when(employeeDetailsRepository.findActiveAdmins()).thenReturn(List.of(adminEmployee));
        when(employeeDetailsRepository.save(any())).thenReturn(managerEmployee);

        employeeService.toggleEmployeeStatus(2L);

        assertThat(subordinate1.getManager()).isEqualTo(adminEmployee);
    }

    // ── reassignSubordinates — no subordinates (no-op) ─────────────────────

    @Test
    void toggleStatus_deactivateManagerWithNoSubordinates_noReassignment() {
        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(managerEmployee));
        when(employeeDetailsRepository.findByManagerId(2L)).thenReturn(List.of());
        when(employeeDetailsRepository.save(any())).thenReturn(managerEmployee);

        employeeService.toggleEmployeeStatus(2L);

        // findActiveAdmins should NOT be called since there are no subordinates
        verify(employeeDetailsRepository, never()).findActiveAdmins();
        // Only manager itself is saved
        verify(employeeDetailsRepository, times(1)).save(managerEmployee);
    }

    // ── reassignSubordinates — multiple subordinates ─────────────────────────

    @Test
    void toggleStatus_deactivateManager_multipleSubordinatesAllReassigned() {
        EmployeeDetails sub3User = EmployeeDetails.builder().id(5L)
                .user(User.builder().id(5L).email("sub3@company.com").role(Role.EMPLOYEE).active(true).build())
                .firstName("Sub").lastName("Three").employeeCode("10005").active(true)
                .manager(managerEmployee).build();

        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(managerEmployee));
        when(employeeDetailsRepository.findByManagerId(2L))
                .thenReturn(List.of(subordinate1, subordinate2, sub3User));
        when(employeeDetailsRepository.save(any())).thenReturn(managerEmployee);

        employeeService.toggleEmployeeStatus(2L);

        assertThat(subordinate1.getManager()).isEqualTo(reportingManager);
        assertThat(subordinate2.getManager()).isEqualTo(reportingManager);
        assertThat(sub3User.getManager()).isEqualTo(reportingManager);
    }

    // ── reassignSubordinates — admin deactivated ─────────────────────────────

    @Test
    void toggleStatus_deactivateAdmin_subordinatesReassignedToAnotherAdmin() {
        EmployeeDetails anotherAdmin = EmployeeDetails.builder().id(20L)
                .user(User.builder().id(20L).email("admin2@company.com").role(Role.ADMIN).active(true).build())
                .firstName("Admin2").lastName("User").employeeCode("10020").active(true).build();

        // adminEmployee being deactivated has no reporting manager
        adminEmployee.setManager(null);
        subordinate1.setManager(adminEmployee);

        when(employeeDetailsRepository.findById(1L)).thenReturn(Optional.of(adminEmployee));
        when(employeeDetailsRepository.findByManagerId(1L)).thenReturn(List.of(subordinate1));
        when(employeeDetailsRepository.findActiveAdmins()).thenReturn(List.of(adminEmployee, anotherAdmin));
        when(employeeDetailsRepository.save(any())).thenReturn(adminEmployee);

        employeeService.toggleEmployeeStatus(1L);

        // Should NOT reassign to itself (deactivated admin filtered out), use anotherAdmin
        assertThat(subordinate1.getManager()).isEqualTo(anotherAdmin);
    }

    @Test
    void toggleStatus_deactivateLastAdmin_noAdminsAvailable_subordinatesSetToNull() {
        adminEmployee.setManager(null);
        subordinate1.setManager(adminEmployee);

        when(employeeDetailsRepository.findById(1L)).thenReturn(Optional.of(adminEmployee));
        when(employeeDetailsRepository.findByManagerId(1L)).thenReturn(List.of(subordinate1));
        when(employeeDetailsRepository.findActiveAdmins()).thenReturn(List.of(adminEmployee));
        when(employeeDetailsRepository.save(any())).thenReturn(adminEmployee);

        employeeService.toggleEmployeeStatus(1L);

        // All admins filter out self, result is empty, so manager becomes null
        assertThat(subordinate1.getManager()).isNull();
    }

    // ── toggleStatus — deactivates employee ──────────────────────────────────

    @Test
    void toggleStatus_activeEmployee_deactivates() {
        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(managerEmployee));
        when(employeeDetailsRepository.findByManagerId(2L)).thenReturn(List.of());
        when(employeeDetailsRepository.save(any())).thenReturn(managerEmployee);

        employeeService.toggleEmployeeStatus(2L);

        assertThat(managerEmployee.isActive()).isFalse();
        assertThat(managerUser.isActive()).isFalse();
    }

    @Test
    void toggleStatus_inactiveEmployee_reactivates() {
        managerEmployee.setActive(false);
        managerUser.setActive(false);

        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(managerEmployee));
        when(employeeDetailsRepository.save(any())).thenReturn(managerEmployee);

        employeeService.toggleEmployeeStatus(2L);

        assertThat(managerEmployee.isActive()).isTrue();
        assertThat(managerUser.isActive()).isTrue();
    }

    @Test
    void toggleStatus_reactivate_doesNotCallReassign() {
        managerEmployee.setActive(false);
        managerUser.setActive(false);

        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(managerEmployee));
        when(employeeDetailsRepository.save(any())).thenReturn(managerEmployee);

        employeeService.toggleEmployeeStatus(2L);

        verify(employeeDetailsRepository, never()).findByManagerId(any());
    }

    // ── deleteEmployee ────────────────────────────────────────────────────────

    @Test
    void deleteEmployee_deactivatesAndReassigns() {
        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(managerEmployee));
        when(employeeDetailsRepository.findByManagerId(2L)).thenReturn(List.of(subordinate1));
        when(employeeDetailsRepository.save(any())).thenReturn(managerEmployee);

        employeeService.deleteEmployee(2L);

        assertThat(managerEmployee.isActive()).isFalse();
        assertThat(subordinate1.getManager()).isEqualTo(reportingManager);
    }

    @Test
    void deleteEmployee_nonExistingId_throwsResourceNotFoundException() {
        when(employeeDetailsRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.deleteEmployee(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── toggleStatus — not found ──────────────────────────────────────────────

    @Test
    void toggleStatus_nonExistingId_throwsResourceNotFoundException() {
        when(employeeDetailsRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.toggleEmployeeStatus(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
