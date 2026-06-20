package com.emp.management.service;

import com.emp.management.dto.LeaveDTO;
import com.emp.management.entity.*;
import com.emp.management.exception.BadRequestException;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.EmployeeDetailsRepository;
import com.emp.management.repository.LeaveRepository;
import com.emp.management.repository.TimesheetEntryRepository;
import com.emp.management.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveServiceTest {

    @Mock private LeaveRepository leaveRepository;
    @Mock private EmployeeDetailsRepository employeeDetailsRepository;
    @Mock private TimesheetEntryRepository timesheetEntryRepository;
    @Mock private UserRepository userRepository;
    @Mock private EmailService emailService;
    @Mock private HolidayService holidayService;

    @InjectMocks private LeaveService leaveService;

    private EmployeeDetails employee;
    private EmployeeDetails manager;
    private Leave pendingLeave;

    @BeforeEach
    void setUp() {
        User mgrUser = User.builder().id(1L).email("mgr@company.com").role(Role.MANAGER).active(true).build();
        manager = EmployeeDetails.builder().id(1L).user(mgrUser).firstName("Jane").lastName("Manager").active(true).build();
        mgrUser.setEmployeeDetails(manager);

        User empUser = User.builder().id(2L).email("emp@company.com").role(Role.EMPLOYEE).active(true).build();
        employee = EmployeeDetails.builder().id(2L).user(empUser).firstName("John").lastName("Doe")
                .manager(manager).active(true).build();
        empUser.setEmployeeDetails(employee);

        pendingLeave = Leave.builder()
                .id(10L)
                .employee(employee)
                .leaveType("Annual Leave")
                .startDate(LocalDate.of(2026, 7, 7))
                .endDate(LocalDate.of(2026, 7, 9))
                .totalDays(3)
                .reason("Vacation")
                .status(LeaveStatus.PENDING)
                .build();
    }

    // ── Apply Leave ──────────────────────────────────────────────────────────

    @Test
    void applyLeave_validFutureDates_createsLeave() {
        LocalDate start = LocalDate.now().plusDays(5);
        LocalDate end = LocalDate.now().plusDays(7);
        LeaveDTO dto = buildLeaveDTO(start, end, "Annual Leave", "Trip");

        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));
        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(employee));
        when(leaveRepository.existsOverlappingLeave(anyLong(), any(), any(), anyList())).thenReturn(false);
        when(holidayService.countDeductibleDays(any(), any(), any(), any())).thenReturn(3);
        when(holidayService.getApplicableHolidayDates(any(), any(), any(), any())).thenReturn(Set.of());
        when(leaveRepository.save(any())).thenReturn(pendingLeave);
        when(timesheetEntryRepository.existsByEmployeeIdAndProjectNameAndDate(anyLong(), anyString(), any())).thenReturn(false);
        when(timesheetEntryRepository.save(any())).thenReturn(null);
        when(userRepository.findByRole(Role.HR)).thenReturn(List.of());
        when(userRepository.findByRoleIn(anyList())).thenReturn(List.of());
        doNothing().when(emailService).sendLeaveRequestEmail(anyString(), any(String[].class),
                anyString(), anyString(), anyString(), any(), any(), anyInt(), anyString());

        LeaveDTO result = leaveService.applyLeave(2L, dto, "emp@company.com");

        assertThat(result).isNotNull();
        verify(leaveRepository).save(any(Leave.class));
    }

    @Test
    void applyLeave_startDateInPast_throwsBadRequestException() {
        LocalDate past = LocalDate.now().minusDays(1);
        LeaveDTO dto = buildLeaveDTO(past, past.plusDays(2), "Annual Leave", "Trip");

        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));
        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> leaveService.applyLeave(2L, dto, "emp@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Start date cannot be in the past");
    }

    @Test
    void applyLeave_endBeforeStart_throwsBadRequestException() {
        LocalDate start = LocalDate.now().plusDays(5);
        LocalDate end = LocalDate.now().plusDays(2);
        LeaveDTO dto = buildLeaveDTO(start, end, "Annual Leave", "Trip");

        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));
        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> leaveService.applyLeave(2L, dto, "emp@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("End date cannot be before start date");
    }

    @Test
    void applyLeave_overlappingLeave_throwsBadRequestException() {
        LocalDate start = LocalDate.now().plusDays(1);
        LeaveDTO dto = buildLeaveDTO(start, start.plusDays(1), "Annual Leave", "Trip");

        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));
        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(employee));
        when(leaveRepository.existsOverlappingLeave(anyLong(), any(), any(), anyList())).thenReturn(true);

        assertThatThrownBy(() -> leaveService.applyLeave(2L, dto, "emp@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already have a leave application");
    }

    @Test
    void applyLeave_exceedsDuration30Days_throwsBadRequestException() {
        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate end = start.plusDays(31);
        LeaveDTO dto = buildLeaveDTO(start, end, "Annual Leave", "Long trip");

        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));
        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> leaveService.applyLeave(2L, dto, "emp@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("30 calendar days");
    }

    @Test
    void applyLeave_allWeekendDates_throwsBadRequestException() {
        // Saturday and Sunday only
        LocalDate saturday = LocalDate.now().with(java.time.DayOfWeek.SATURDAY).plusWeeks(1);
        LocalDate sunday = saturday.plusDays(1);
        LeaveDTO dto = buildLeaveDTO(saturday, sunday, "Annual Leave", "Weekend trip");

        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));
        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(employee));
        when(leaveRepository.existsOverlappingLeave(anyLong(), any(), any(), anyList())).thenReturn(false);
        when(holidayService.countDeductibleDays(any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> leaveService.applyLeave(2L, dto, "emp@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("weekends or public holidays");
    }

    @Test
    void applyLeave_employeeNotFound_throwsResourceNotFoundException() {
        LocalDate start = LocalDate.now().plusDays(5);
        LeaveDTO dto = buildLeaveDTO(start, start.plusDays(2), "Annual Leave", "Trip");

        // Caller is employee with id=2; target is 99 which doesn't exist
        // requireSelfOrPrivileged is called first — we need to make the caller privileged
        // so that access check passes and then the findById(99) throws RNFE
        when(employeeDetailsRepository.findByUserEmail("admin@company.com")).thenReturn(Optional.of(adminEmployee()));
        when(employeeDetailsRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaveService.applyLeave(99L, dto, "admin@company.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private EmployeeDetails adminEmployee() {
        User adminUser = User.builder().id(99L).email("admin@company.com").role(Role.ADMIN).active(true).build();
        return EmployeeDetails.builder().id(99L).user(adminUser).firstName("Admin").lastName("User").active(true).build();
    }

    // ── Update Leave Status ──────────────────────────────────────────────────

    @Test
    void updateLeaveStatus_pendingToApproved_returnsApprovedDTO() {
        when(leaveRepository.findById(10L)).thenReturn(Optional.of(pendingLeave));
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com")).thenReturn(Optional.of(manager));
        when(leaveRepository.save(any())).thenReturn(pendingLeave);
        when(userRepository.findByRole(Role.HR)).thenReturn(List.of());
        when(userRepository.findByRoleIn(anyList())).thenReturn(List.of());
        doNothing().when(emailService).sendLeaveDecisionEmail(anyString(), any(String[].class),
                anyString(), anyString(), anyString(), any(), any(), anyString(), anyString());

        LeaveDTO result = leaveService.updateLeaveStatus(10L, "mgr@company.com", LeaveStatus.APPROVED, "Approved");

        assertThat(result).isNotNull();
        verify(leaveRepository).save(pendingLeave);
    }

    @Test
    void updateLeaveStatus_pendingToRejected_removesTimesheetEntries() {
        when(leaveRepository.findById(10L)).thenReturn(Optional.of(pendingLeave));
        when(employeeDetailsRepository.findByUserEmail("mgr@company.com")).thenReturn(Optional.of(manager));
        when(leaveRepository.save(any())).thenReturn(pendingLeave);
        when(userRepository.findByRole(Role.HR)).thenReturn(List.of());
        when(userRepository.findByRoleIn(anyList())).thenReturn(List.of());
        doNothing().when(timesheetEntryRepository).deleteByEmployeeIdAndProjectNameAndDateBetween(
                anyLong(), anyString(), any(), any());
        doNothing().when(emailService).sendLeaveDecisionEmail(anyString(), any(String[].class),
                anyString(), anyString(), anyString(), any(), any(), anyString(), anyString());

        leaveService.updateLeaveStatus(10L, "mgr@company.com", LeaveStatus.REJECTED, "Not approved");

        verify(timesheetEntryRepository).deleteByEmployeeIdAndProjectNameAndDateBetween(
                eq(2L), eq("LEAVE"), any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    void updateLeaveStatus_alreadyProcessed_throwsBadRequestException() {
        pendingLeave.setStatus(LeaveStatus.APPROVED);
        when(leaveRepository.findById(10L)).thenReturn(Optional.of(pendingLeave));

        assertThatThrownBy(() -> leaveService.updateLeaveStatus(10L, "mgr@company.com", LeaveStatus.REJECTED, ""))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already processed");
    }

    @Test
    void updateLeaveStatus_leaveNotFound_throwsResourceNotFoundException() {
        when(leaveRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaveService.updateLeaveStatus(99L, "mgr@company.com", LeaveStatus.APPROVED, "ok"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Delete Leave ─────────────────────────────────────────────────────────

    @Test
    void deleteLeave_pendingLeave_deletesSuccessfully() {
        when(leaveRepository.findById(10L)).thenReturn(Optional.of(pendingLeave));
        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));
        doNothing().when(timesheetEntryRepository).deleteByEmployeeIdAndProjectNameAndDateBetween(
                anyLong(), anyString(), any(), any());
        doNothing().when(leaveRepository).delete(pendingLeave);

        leaveService.deleteLeave(10L, "emp@company.com");

        verify(leaveRepository).delete(pendingLeave);
    }

    @Test
    void deleteLeave_nonPendingLeave_throwsBadRequestException() {
        pendingLeave.setStatus(LeaveStatus.APPROVED);
        when(leaveRepository.findById(10L)).thenReturn(Optional.of(pendingLeave));
        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> leaveService.deleteLeave(10L, "emp@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only pending leaves can be deleted");
    }

    @Test
    void deleteLeave_nonExistingLeave_throwsResourceNotFoundException() {
        when(leaveRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaveService.deleteLeave(99L, "emp@company.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    @Test
    void getMyLeaves_returnsLeavesForEmployee() {
        when(leaveRepository.findByEmployeeIdOrderByAppliedAtDesc(2L)).thenReturn(List.of(pendingLeave));
        when(employeeDetailsRepository.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));

        List<LeaveDTO> result = leaveService.getMyLeaves(2L, "emp@company.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLeaveType()).isEqualTo("Annual Leave");
    }

    @Test
    void getAllPendingLeaves_returnsOnlyPending() {
        when(leaveRepository.findByStatus(LeaveStatus.PENDING)).thenReturn(List.of(pendingLeave));

        List<LeaveDTO> result = leaveService.getAllPendingLeaves();

        assertThat(result).hasSize(1);
    }

    @Test
    void getAllLeaves_delegatesToRepository() {
        when(leaveRepository.findAllOrderByPendingFirst()).thenReturn(List.of(pendingLeave));

        List<LeaveDTO> result = leaveService.getAllLeaves();

        assertThat(result).hasSize(1);
    }

    @Test
    void getPendingLeavesForManager_returnsPendingOnly() {
        when(leaveRepository.findByManagerIdAndStatus(1L, LeaveStatus.PENDING)).thenReturn(List.of(pendingLeave));

        List<LeaveDTO> result = leaveService.getPendingLeavesForManager(1L);

        assertThat(result).hasSize(1);
    }

    private LeaveDTO buildLeaveDTO(LocalDate start, LocalDate end, String type, String reason) {
        return LeaveDTO.builder()
                .startDate(start)
                .endDate(end)
                .leaveType(type)
                .reason(reason)
                .build();
    }
}
