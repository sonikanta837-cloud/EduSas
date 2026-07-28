package com.emp.management.repository;

import com.emp.management.entity.JobWorkSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobWorkSessionRepository extends JpaRepository<JobWorkSession, Long> {

    List<JobWorkSession> findByEmployeeIdAndWorkDateOrderByLoginTimeAsc(Long employeeId, LocalDate workDate);

    List<JobWorkSession> findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAscLoginTimeAsc(
            Long employeeId, LocalDate start, LocalDate end);

    Optional<JobWorkSession> findByEmployeeIdAndOpenFlagTrue(Long employeeId);

    // Most recent closed session for an employee — used by the break monitor
    // to find the logout time that started their current break.
    Optional<JobWorkSession> findTopByEmployeeIdAndLogoutTimeIsNotNullOrderByLogoutTimeDesc(Long employeeId);

    List<JobWorkSession> findByOpenFlagTrue();

    @Query("SELECT s FROM JobWorkSession s JOIN FETCH s.employee emp " +
           "WHERE s.workDate BETWEEN :start AND :end " +
           "ORDER BY s.workDate ASC, emp.lastName ASC, emp.firstName ASC")
    List<JobWorkSession> findAllForWorkReport(@Param("start") LocalDate start, @Param("end") LocalDate end);

    // Bulk "all employees, one day" lookup — used for live org-wide today aggregates
    // (dashboard KPIs, team reports) instead of one query per employee.
    @Query("SELECT s FROM JobWorkSession s JOIN FETCH s.employee emp WHERE s.workDate = :date")
    List<JobWorkSession> findAllByWorkDate(@Param("date") LocalDate date);
}
