package com.emp.management.service;

import com.emp.management.dto.DashboardDTO;
import com.emp.management.entity.LeaveStatus;
import com.emp.management.entity.Role;
import com.emp.management.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private EmployeeDetailsRepository employeeDetailsRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private LeaveRepository leaveRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private TimesheetRepository timesheetRepository;

    @InjectMocks private DashboardService dashboardService;

    @Test
    void getDashboardStats_returnsPopulatedDTO() {
        when(employeeDetailsRepository.count()).thenReturn(50L);
        when(employeeDetailsRepository.countByActive(true)).thenReturn(45L);
        when(leaveRepository.countByStatus(LeaveStatus.PENDING)).thenReturn(5L);
        when(courseRepository.countByActive(true)).thenReturn(8L);
        when(enrollmentRepository.getOverallCompletionPercentage()).thenReturn(72.5);
        when(timesheetRepository.getTotalWorkHoursByDate(any(LocalDate.class))).thenReturn(360.0);
        when(employeeDetailsRepository.countByUserRoleInAndActiveTrue(anyList())).thenReturn(10L);

        DashboardDTO result = dashboardService.getDashboardStats();

        assertThat(result).isNotNull();
        assertThat(result.getTotalEmployees()).isEqualTo(50L);
        assertThat(result.getActiveEmployees()).isEqualTo(45L);
        assertThat(result.getPendingLeaves()).isEqualTo(5L);
        assertThat(result.getActiveCourses()).isEqualTo(8L);
        assertThat(result.getAvgCourseCompletion()).isEqualTo(72.5);
        assertThat(result.getTotalWorkHoursToday()).isEqualTo(360.0);
        assertThat(result.getTotalManagers()).isEqualTo(10L);
    }

    @Test
    void getDashboardStats_nullTimesheetHours_returnsZero() {
        when(employeeDetailsRepository.count()).thenReturn(10L);
        when(employeeDetailsRepository.countByActive(true)).thenReturn(10L);
        when(leaveRepository.countByStatus(LeaveStatus.PENDING)).thenReturn(0L);
        when(courseRepository.countByActive(true)).thenReturn(0L);
        when(enrollmentRepository.getOverallCompletionPercentage()).thenReturn(null); // null completion
        when(timesheetRepository.getTotalWorkHoursByDate(any(LocalDate.class))).thenReturn(null); // null hours
        when(employeeDetailsRepository.countByUserRoleInAndActiveTrue(anyList())).thenReturn(0L);

        DashboardDTO result = dashboardService.getDashboardStats();

        assertThat(result.getTotalWorkHoursToday()).isEqualTo(0.0);
        assertThat(result.getAvgCourseCompletion()).isEqualTo(0.0);
    }

    @Test
    void getDashboardStats_zeroEmployees_returnsZeroCounts() {
        when(employeeDetailsRepository.count()).thenReturn(0L);
        when(employeeDetailsRepository.countByActive(true)).thenReturn(0L);
        when(leaveRepository.countByStatus(LeaveStatus.PENDING)).thenReturn(0L);
        when(courseRepository.countByActive(true)).thenReturn(0L);
        when(enrollmentRepository.getOverallCompletionPercentage()).thenReturn(0.0);
        when(timesheetRepository.getTotalWorkHoursByDate(any(LocalDate.class))).thenReturn(0.0);
        when(employeeDetailsRepository.countByUserRoleInAndActiveTrue(anyList())).thenReturn(0L);

        DashboardDTO result = dashboardService.getDashboardStats();

        assertThat(result.getTotalEmployees()).isEqualTo(0L);
        assertThat(result.getPendingLeaves()).isEqualTo(0L);
    }
}
