package com.emp.management.repository;

import com.emp.management.entity.DailyAttendanceStatus;
import com.emp.management.entity.JobDailySummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobDailySummaryRepository extends JpaRepository<JobDailySummary, Long> {

    Optional<JobDailySummary> findByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);

    List<JobDailySummary> findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(
            Long employeeId, LocalDate start, LocalDate end);

    List<JobDailySummary> findByWorkDateAndStatusAndUnderHoursAlertSentFalse(
            LocalDate workDate, DailyAttendanceStatus status);
}
