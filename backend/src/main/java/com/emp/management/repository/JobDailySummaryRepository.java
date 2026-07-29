package com.emp.management.repository;

import com.emp.management.entity.DailyAttendanceStatus;
import com.emp.management.entity.JobDailySummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // Batched lookup so the live "today" DTO paths can attach correctionStatus for
    // every employee in one query instead of one findByEmployeeIdAndWorkDate per employee.
    List<JobDailySummary> findByWorkDate(LocalDate workDate);

    @Query("SELECT s FROM JobDailySummary s JOIN FETCH s.employee emp " +
           "WHERE s.workDate BETWEEN :start AND :end " +
           "ORDER BY s.workDate ASC, emp.lastName ASC, emp.firstName ASC")
    List<JobDailySummary> findAllForReport(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT s FROM JobDailySummary s JOIN FETCH s.employee emp " +
           "WHERE emp.manager.id = :managerId AND s.workDate BETWEEN :start AND :end " +
           "ORDER BY s.workDate ASC, emp.lastName ASC, emp.firstName ASC")
    List<JobDailySummary> findByManagerForReport(@Param("managerId") Long managerId,
                                                  @Param("start") LocalDate start, @Param("end") LocalDate end);
}
