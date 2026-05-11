package com.emp.management.service;

import com.emp.management.dto.DashboardDTO;
import com.emp.management.entity.LeaveStatus;
import com.emp.management.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final EmployeeDetailsRepository employeeDetailsRepository;
    private final CourseRepository courseRepository;
    private final LeaveRepository leaveRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final TimesheetRepository timesheetRepository;

    public DashboardDTO getDashboardStats() {
        Double totalHours = timesheetRepository.getTotalWorkHoursByDate(LocalDate.now());
        Double completionPct = enrollmentRepository.getOverallCompletionPercentage();

        return DashboardDTO.builder()
                .totalEmployees(employeeDetailsRepository.count())
                .activeEmployees(employeeDetailsRepository.countByActive(true))
                .pendingLeaves(leaveRepository.countByStatus(LeaveStatus.PENDING))
                .activeCourses(courseRepository.countByActive(true))
                .avgCourseCompletion(completionPct != null ? Math.round(completionPct * 100.0) / 100.0 : 0)
                .totalWorkHoursToday(totalHours != null ? totalHours : 0)
                .totalManagers(employeeDetailsRepository.countByManagerIsNotNull())
                .build();
    }
}
