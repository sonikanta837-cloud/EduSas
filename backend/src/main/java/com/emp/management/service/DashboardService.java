package com.emp.management.service;

import com.emp.management.dto.DashboardDTO;
import com.emp.management.dto.JobDailySummaryDTO;
import com.emp.management.entity.LeaveStatus;
import com.emp.management.entity.Role;
import com.emp.management.repository.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final EmployeeDetailsRepository employeeDetailsRepository;
    private final CourseRepository courseRepository;
    private final LeaveRepository leaveRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final JobDailySummaryService jobDailySummaryService;

    public DashboardDTO getDashboardStats() {
        Double completionPct = enrollmentRepository.getOverallCompletionPercentage();

        List<JobDailySummaryDTO> todaySummaries = jobDailySummaryService.getTodayLiveSummaries();
        double totalWorkMinutesToday = todaySummaries.stream()
                .mapToInt(JobDailySummaryDTO::getTotalWorkingMinutes).sum();
        long presentToday = todaySummaries.stream().filter(s -> "PRESENT".equals(s.getStatus())).count();
        long underHoursToday = todaySummaries.stream().filter(s -> "UNDER_HOURS".equals(s.getStatus())).count();
        long overtimeToday = todaySummaries.stream().filter(s -> "OVERTIME".equals(s.getStatus())).count();
        long absentToday = todaySummaries.stream().filter(s -> "ABSENT".equals(s.getStatus())).count();
        long onLeaveToday = todaySummaries.stream().filter(s -> "LEAVE".equals(s.getStatus())).count();

        return DashboardDTO.builder()
                .totalEmployees(employeeDetailsRepository.count())
                .activeEmployees(employeeDetailsRepository.countByActive(true))
                .pendingLeaves(leaveRepository.countByStatus(LeaveStatus.PENDING))
                .activeCourses(courseRepository.countByActive(true))
                .avgCourseCompletion(completionPct != null ? Math.round(completionPct * 100.0) / 100.0 : 0)
                .totalWorkHoursToday(Math.round(totalWorkMinutesToday / 60.0 * 100.0) / 100.0)
                .totalManagers(employeeDetailsRepository.countByUserRoleInAndActiveTrue(List.of(Role.MANAGER, Role.ASSISTANT_MANAGER)))
                .presentToday(presentToday)
                .underHoursToday(underHoursToday)
                .overtimeToday(overtimeToday)
                .absentToday(absentToday)
                .onLeaveToday(onLeaveToday)
                .build();
    }
}
