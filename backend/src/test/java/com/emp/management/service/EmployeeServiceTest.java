package com.emp.management.service;

import com.emp.management.dto.EmployeeDTO;
import com.emp.management.dto.RegisterRequest;
import com.emp.management.entity.EmployeeDetails;
import com.emp.management.entity.Role;
import com.emp.management.entity.User;
import com.emp.management.exception.BadRequestException;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.EmployeeDetailsRepository;
import com.emp.management.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock private EmployeeDetailsRepository employeeDetailsRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private EmployeeService employeeService;

    private User adminUser;
    private EmployeeDetails adminEmployee;
    private User empUser;
    private EmployeeDetails employee;

    @BeforeEach
    void setUp() {
        adminUser = User.builder().id(1L).email("admin@company.com").role(Role.ADMIN).active(true).build();
        adminEmployee = EmployeeDetails.builder().id(1L).user(adminUser).firstName("Admin").lastName("User")
                .employeeCode("10001").active(true).build();
        adminUser.setEmployeeDetails(adminEmployee);

        empUser = User.builder().id(2L).email("emp@company.com").role(Role.EMPLOYEE).active(true).build();
        employee = EmployeeDetails.builder().id(2L).user(empUser).firstName("John").lastName("Doe")
                .employeeCode("10002").active(true).build();
        empUser.setEmployeeDetails(employee);
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    @Test
    void getAllEmployees_returnsActiveEmployees() {
        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(adminEmployee, employee));

        List<EmployeeDTO> result = employeeService.getAllEmployees();

        assertThat(result).hasSize(2);
    }

    @Test
    void getEmployeeById_existingId_returnsDTO() {
        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(employee));

        EmployeeDTO dto = employeeService.getEmployeeById(2L);

        assertThat(dto.getFirstName()).isEqualTo("John");
        assertThat(dto.getLastName()).isEqualTo("Doe");
    }

    @Test
    void getEmployeeById_nonExistingId_throwsResourceNotFoundException() {
        when(employeeDetailsRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.getEmployeeById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getEmployeeById_withRequester_adminCanViewInactiveEmployee() {
        employee.setActive(false);
        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(employee));
        when(employeeDetailsRepository.findByUserEmail("admin@company.com")).thenReturn(Optional.of(adminEmployee));

        EmployeeDTO dto = employeeService.getEmployeeById(2L, "admin@company.com");

        assertThat(dto.getId()).isEqualTo(2L);
    }

    @Test
    void getEmployeeById_withRequester_nonAdminCannotViewInactive() {
        employee.setActive(false);
        EmployeeDetails otherEmp = EmployeeDetails.builder().id(3L)
                .user(User.builder().id(3L).email("other@company.com").role(Role.EMPLOYEE).active(true).build())
                .active(true).build();

        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(employee));
        when(employeeDetailsRepository.findByUserEmail("other@company.com")).thenReturn(Optional.of(otherEmp));

        assertThatThrownBy(() -> employeeService.getEmployeeById(2L, "other@company.com"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getExEmployees_returnsInactiveEmployees() {
        employee.setActive(false);
        when(employeeDetailsRepository.findByActive(false)).thenReturn(List.of(employee));

        List<EmployeeDTO> result = employeeService.getExEmployees();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isActive()).isFalse();
    }

    @Test
    void searchEmployees_delegatesToRepository() {
        when(employeeDetailsRepository.searchActiveEmployees("John")).thenReturn(List.of(employee));

        List<EmployeeDTO> result = employeeService.searchEmployees("John");

        assertThat(result).hasSize(1);
        verify(employeeDetailsRepository).searchActiveEmployees("John");
    }

    // ── Create ───────────────────────────────────────────────────────────────

    @Test
    void createEmployee_newEmail_returnsDTO() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("new@company.com");
        req.setPassword("pass");
        req.setFirstName("New");
        req.setLastName("Hire");
        req.setRole(Role.EMPLOYEE);

        User savedUser = User.builder().id(3L).email("new@company.com").role(Role.EMPLOYEE).active(true).build();
        EmployeeDetails savedEmp = EmployeeDetails.builder().id(3L).user(savedUser)
                .firstName("New").lastName("Hire").employeeCode("10003").active(true).build();
        savedUser.setEmployeeDetails(savedEmp);

        when(userRepository.existsByEmail("new@company.com")).thenReturn(false);
        when(passwordEncoder.encode("pass")).thenReturn("encoded");
        when(userRepository.save(any())).thenReturn(savedUser);
        when(employeeDetailsRepository.findAllEmployeeCodes()).thenReturn(List.of("10001", "10002"));
        when(employeeDetailsRepository.save(any())).thenReturn(savedEmp);

        EmployeeDTO dto = employeeService.createEmployee(req, "admin@company.com");

        assertThat(dto.getFirstName()).isEqualTo("New");
    }

    @Test
    void createEmployee_duplicateEmail_throwsBadRequestException() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("emp@company.com");
        req.setPassword("pass");
        req.setFirstName("John");
        req.setLastName("Doe");
        req.setRole(Role.EMPLOYEE);

        when(userRepository.existsByEmail("emp@company.com")).thenReturn(true);

        assertThatThrownBy(() -> employeeService.createEmployee(req, "admin@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already registered");
    }

    // ── Update ───────────────────────────────────────────────────────────────

    @Test
    void updateEmployee_selfUpdate_succeeds() {
        EmployeeDTO dto = new EmployeeDTO();
        dto.setFirstName("Johnny");
        dto.setLastName("Doe");
        dto.setEmail("emp@company.com");

        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(employee));
        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));
        when(userRepository.save(any())).thenReturn(empUser);
        when(employeeDetailsRepository.save(any())).thenReturn(employee);

        EmployeeDTO result = employeeService.updateEmployee(2L, dto, "emp@company.com");

        assertThat(result).isNotNull();
    }

    @Test
    void updateEmployee_selfAsOwnManager_throwsBadRequestException() {
        EmployeeDTO dto = new EmployeeDTO();
        dto.setFirstName("John");
        dto.setLastName("Doe");
        dto.setManagerId(2L); // self

        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(employee));
        when(employeeDetailsRepository.findByUserEmail("admin@company.com")).thenReturn(Optional.of(adminEmployee));

        assertThatThrownBy(() -> employeeService.updateEmployee(2L, dto, "admin@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("own manager");
    }

    @Test
    void updateEmployee_circularChain_throwsBadRequestException() {
        // employee(2) reports to admin(1); setting admin's manager to employee creates cycle
        adminEmployee.setManager(null);

        EmployeeDetails managerEmp = EmployeeDetails.builder().id(3L)
                .user(User.builder().id(3L).email("mgr@company.com").role(Role.MANAGER).active(true).build())
                .active(true).manager(adminEmployee).build();

        // Try to set admin's manager to managerEmp whose manager is already admin
        EmployeeDTO dto = new EmployeeDTO();
        dto.setFirstName("Admin");
        dto.setLastName("User");
        dto.setManagerId(3L);

        when(employeeDetailsRepository.findById(1L)).thenReturn(Optional.of(adminEmployee));
        when(employeeDetailsRepository.findByUserEmail("admin@company.com")).thenReturn(Optional.of(adminEmployee));
        // Walking chain from 3 -> adminEmployee(1) == target(1): cycle detected
        when(employeeDetailsRepository.findById(3L)).thenReturn(Optional.of(managerEmp));

        assertThatThrownBy(() -> employeeService.updateEmployee(1L, dto, "admin@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("circular");
    }

    // ── Toggle Status ────────────────────────────────────────────────────────

    @Test
    void toggleEmployeeStatus_activeEmployee_deactivatesAndTransfersSubordinates() {
        EmployeeDetails subordinate = EmployeeDetails.builder().id(5L)
                .user(User.builder().id(5L).email("sub@company.com").role(Role.EMPLOYEE).active(true).build())
                .manager(employee).active(true).build();

        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(employee));
        when(employeeDetailsRepository.findByManagerId(2L)).thenReturn(List.of(subordinate));
        when(employeeDetailsRepository.findActiveAdmins()).thenReturn(List.of(adminEmployee));
        when(employeeDetailsRepository.save(any())).thenReturn(employee);

        employeeService.toggleEmployeeStatus(2L);

        assertThat(employee.isActive()).isFalse();
        assertThat(subordinate.getManager()).isEqualTo(adminEmployee);
    }

    @Test
    void toggleEmployeeStatus_inactiveEmployee_reactivates() {
        employee.setActive(false);
        empUser.setActive(false);

        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(employee));
        when(employeeDetailsRepository.save(any())).thenReturn(employee);

        employeeService.toggleEmployeeStatus(2L);

        assertThat(employee.isActive()).isTrue();
    }

    @Test
    void toggleEmployeeStatus_nonExistingId_throwsResourceNotFoundException() {
        when(employeeDetailsRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.toggleEmployeeStatus(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    @Test
    void deleteEmployee_deactivatesEmployee() {
        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(employee));
        when(employeeDetailsRepository.findByManagerId(2L)).thenReturn(List.of());
        when(employeeDetailsRepository.save(any())).thenReturn(employee);

        employeeService.deleteEmployee(2L);

        assertThat(employee.isActive()).isFalse();
        assertThat(empUser.isActive()).isFalse();
    }

    @Test
    void deleteEmployee_nonExistingId_throwsResourceNotFoundException() {
        when(employeeDetailsRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.deleteEmployee(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Employee Code Generation ─────────────────────────────────────────────

    @Test
    void createEmployee_autoGeneratesCodeStartingFrom10001() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("first@company.com");
        req.setPassword("pass");
        req.setFirstName("First");
        req.setLastName("Employee");
        req.setRole(Role.EMPLOYEE);

        User savedUser = User.builder().id(10L).email("first@company.com").role(Role.EMPLOYEE).active(true).build();
        EmployeeDetails savedEmp = EmployeeDetails.builder().id(10L).user(savedUser)
                .firstName("First").lastName("Employee").employeeCode("10001").active(true).build();
        savedUser.setEmployeeDetails(savedEmp);

        when(userRepository.existsByEmail("first@company.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(userRepository.save(any())).thenReturn(savedUser);
        when(employeeDetailsRepository.findAllEmployeeCodes()).thenReturn(List.of());
        when(employeeDetailsRepository.save(any())).thenReturn(savedEmp);

        EmployeeDTO dto = employeeService.createEmployee(req, "admin@company.com");

        assertThat(dto.getEmployeeCode()).isEqualTo("10001");
    }
}
