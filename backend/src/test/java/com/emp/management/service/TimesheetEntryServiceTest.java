package com.emp.management.service;

import com.emp.management.dto.TimesheetEntryDTO;
import com.emp.management.entity.*;
import com.emp.management.exception.BadRequestException;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.EmployeeDetailsRepository;
import com.emp.management.repository.TimesheetEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TimesheetEntryServiceTest {

    @Mock private TimesheetEntryRepository repository;
    @Mock private EmployeeDetailsRepository employeeDetailsRepository;

    @InjectMocks private TimesheetEntryService timesheetEntryService;

    private EmployeeDetails employee;
    private EmployeeDetails manager;
    private TimesheetEntry entry;

    @BeforeEach
    void setUp() {
        User mgrUser = User.builder().id(1L).email("mgr@company.com").role(Role.MANAGER).active(true).build();
        manager = EmployeeDetails.builder().id(1L).user(mgrUser).firstName("Jane").lastName("Mgr").active(true).build();
        mgrUser.setEmployeeDetails(manager);

        User empUser = User.builder().id(2L).email("emp@company.com").role(Role.EMPLOYEE).active(true).build();
        employee = EmployeeDetails.builder().id(2L).user(empUser).firstName("John").lastName("Doe")
                .manager(manager).active(true).build();
        empUser.setEmployeeDetails(employee);

        entry = TimesheetEntry.builder()
                .id(10L)
                .employee(employee)
                .date(LocalDate.now())
                .projectName("EmpSAS")
                .taskName("Development")
                .hours(8.0)
                .build();
    }

    // ── getMonthlyEntries ─────────────────────────────────────────────────────

    @Test
    void getMonthlyEntries_returnsEntriesForMonth() {
        when(repository.findByEmployeeIdAndDateBetweenOrderByDateAsc(anyLong(), any(), any()))
                .thenReturn(List.of(entry));

        List<TimesheetEntryDTO> result = timesheetEntryService.getMonthlyEntries(2L, 2026, 6);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProjectName()).isEqualTo("EmpSAS");
        assertThat(result.get(0).getHours()).isEqualTo(8.0);
    }

    @Test
    void getMonthlyEntries_noEntries_returnsEmptyList() {
        when(repository.findByEmployeeIdAndDateBetweenOrderByDateAsc(anyLong(), any(), any()))
                .thenReturn(List.of());

        List<TimesheetEntryDTO> result = timesheetEntryService.getMonthlyEntries(2L, 2026, 6);

        assertThat(result).isEmpty();
    }

    // ── saveEntry ─────────────────────────────────────────────────────────────

    @Test
    void saveEntry_newEntry_createsAndReturnsDTO() {
        TimesheetEntryDTO dto = TimesheetEntryDTO.builder()
                .employeeId(2L).date(LocalDate.now()).projectName("EmpSAS")
                .taskName("Dev").hours(8.0).build();

        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));
        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(employee));
        when(repository.save(any())).thenReturn(entry);

        TimesheetEntryDTO result = timesheetEntryService.saveEntry(dto, "emp@company.com");

        assertThat(result).isNotNull();
        assertThat(result.getProjectName()).isEqualTo("EmpSAS");
        verify(repository).save(any(TimesheetEntry.class));
    }

    @Test
    void saveEntry_updateExistingEntry_updatesAndReturnsDTO() {
        TimesheetEntryDTO dto = TimesheetEntryDTO.builder()
                .id(10L).employeeId(2L).date(LocalDate.now()).projectName("EmpSAS")
                .taskName("Testing").hours(7.0).build();

        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));
        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(employee));
        when(repository.findById(10L)).thenReturn(Optional.of(entry));
        when(repository.save(any())).thenReturn(entry);

        TimesheetEntryDTO result = timesheetEntryService.saveEntry(dto, "emp@company.com");

        assertThat(result).isNotNull();
        verify(repository).findById(10L);
    }

    @Test
    void saveEntry_zeroHours_throwsBadRequestException() {
        TimesheetEntryDTO dto = TimesheetEntryDTO.builder()
                .employeeId(2L).date(LocalDate.now()).projectName("EmpSAS")
                .hours(0.0).build();

        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));
        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> timesheetEntryService.saveEntry(dto, "emp@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Hours must be greater than 0");
    }

    @Test
    void saveEntry_hoursOver24_throwsBadRequestException() {
        TimesheetEntryDTO dto = TimesheetEntryDTO.builder()
                .employeeId(2L).date(LocalDate.now()).projectName("EmpSAS")
                .hours(25.0).build();

        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));
        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> timesheetEntryService.saveEntry(dto, "emp@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("at most 24");
    }

    @Test
    void saveEntry_nullDate_throwsBadRequestException() {
        TimesheetEntryDTO dto = TimesheetEntryDTO.builder()
                .employeeId(2L).date(null).projectName("EmpSAS").hours(8.0).build();

        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));
        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> timesheetEntryService.saveEntry(dto, "emp@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Date is required");
    }

    @Test
    void saveEntry_blankProjectName_throwsBadRequestException() {
        TimesheetEntryDTO dto = TimesheetEntryDTO.builder()
                .employeeId(2L).date(LocalDate.now()).projectName("").hours(8.0).build();

        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));
        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> timesheetEntryService.saveEntry(dto, "emp@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Project name is required");
    }

    @Test
    void saveEntry_employeeNotFound_throwsResourceNotFoundException() {
        TimesheetEntryDTO dto = TimesheetEntryDTO.builder()
                .employeeId(99L).date(LocalDate.now()).projectName("EmpSAS").hours(8.0).build();

        User adminUser = User.builder().id(1L).email("admin@company.com").role(Role.ADMIN).active(true).build();
        EmployeeDetails admin = EmployeeDetails.builder().id(1L).user(adminUser).active(true).build();
        adminUser.setEmployeeDetails(admin);

        when(employeeDetailsRepository.findByUserEmail("admin@company.com")).thenReturn(Optional.of(admin));
        when(employeeDetailsRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> timesheetEntryService.saveEntry(dto, "admin@company.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── deleteEntry ───────────────────────────────────────────────────────────

    @Test
    void deleteEntry_ownEntry_deletesSuccessfully() {
        when(repository.findById(10L)).thenReturn(Optional.of(entry));
        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));
        doNothing().when(repository).deleteById(10L);

        timesheetEntryService.deleteEntry(10L, "emp@company.com");

        verify(repository).deleteById(10L);
    }

    @Test
    void deleteEntry_entryNotFound_throwsResourceNotFoundException() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> timesheetEntryService.deleteEntry(999L, "emp@company.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── deleteProjectRows ─────────────────────────────────────────────────────

    @Test
    void deleteProjectRows_asSelf_deletesSuccessfully() {
        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));
        doNothing().when(repository).deleteByEmployeeIdAndProjectNameAndTaskName(2L, "EmpSAS", "Dev");

        timesheetEntryService.deleteProjectRows(2L, "EmpSAS", "Dev", "emp@company.com");

        verify(repository).deleteByEmployeeIdAndProjectNameAndTaskName(2L, "EmpSAS", "Dev");
    }

    @Test
    void deleteProjectRows_asOtherEmployee_throwsAccessDeniedException() {
        User otherUser = User.builder().id(3L).email("other@company.com").role(Role.EMPLOYEE).active(true).build();
        EmployeeDetails other = EmployeeDetails.builder().id(3L).user(otherUser).build();
        otherUser.setEmployeeDetails(other);

        when(employeeDetailsRepository.findByUserEmail("other@company.com")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> timesheetEntryService.deleteProjectRows(2L, "EmpSAS", "Dev", "other@company.com"))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ── verifyAccess ──────────────────────────────────────────────────────────

    @Test
    void verifyAccess_asAdmin_passes() {
        User adminUser = User.builder().id(1L).email("admin@company.com").role(Role.ADMIN).active(true).build();
        EmployeeDetails admin = EmployeeDetails.builder().id(1L).user(adminUser).build();
        adminUser.setEmployeeDetails(admin);

        when(employeeDetailsRepository.findByUserEmail("admin@company.com")).thenReturn(Optional.of(admin));

        // Should not throw
        timesheetEntryService.verifyAccess(2L, "admin@company.com");
    }

    @Test
    void verifyAccess_asManagerForDirectReport_passes() {
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com")).thenReturn(Optional.of(manager));
        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(employee));

        // Should not throw (employee.getManager() == manager)
        timesheetEntryService.verifyAccess(2L, "mgr@company.com");
    }

    @Test
    void verifyAccess_asManagerForSelf_passes() {
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com")).thenReturn(Optional.of(manager));

        // Manager accesses own timesheet (id=1)
        timesheetEntryService.verifyAccess(1L, "mgr@company.com");
    }

    @Test
    void verifyAccess_nullCaller_passes() {
        when(employeeDetailsRepository.findByUserEmail("ghost@company.com")).thenReturn(Optional.empty());

        // Should not throw when caller not found
        timesheetEntryService.verifyAccess(2L, "ghost@company.com");
    }
}
