package com.emp.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDTO {
    private long totalEmployees;
    private long activeEmployees;
    private long pendingLeaves;
    private long activeCourses;
    private double avgCourseCompletion;
    private double totalWorkHoursToday;
    private long pendingReviews;
    private long totalManagers;
    private long presentToday;
    private long underHoursToday;
    private long overtimeToday;
    private long absentToday;
    private long onLeaveToday;
}
