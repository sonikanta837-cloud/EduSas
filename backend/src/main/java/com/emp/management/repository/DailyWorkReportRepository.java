package com.emp.management.repository;

import com.emp.management.entity.DailyWorkReport;
import com.emp.management.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyWorkReportRepository extends JpaRepository<DailyWorkReport, Long> {

    Optional<DailyWorkReport> findByEmployeeIdAndReportDate(Long empId, LocalDate date);

    boolean existsByEmployeeIdAndReportDate(Long empId, LocalDate date);

    List<DailyWorkReport> findByEmployeeIdAndReportDateBetweenOrderByReportDateDesc(
            Long empId, LocalDate start, LocalDate end);

    @Query("SELECT r FROM DailyWorkReport r WHERE r.employee.manager.id = :managerId " +
           "AND r.reportDate BETWEEN :start AND :end " +
           "ORDER BY r.reportDate DESC, r.employee.firstName ASC")
    List<DailyWorkReport> findByManagerAndDateRange(@Param("managerId") Long managerId,
                                                     @Param("start") LocalDate start,
                                                     @Param("end") LocalDate end);

    @Query("SELECT r FROM DailyWorkReport r WHERE r.reportDate BETWEEN :start AND :end " +
           "ORDER BY r.reportDate DESC, r.employee.firstName ASC")
    List<DailyWorkReport> findAllByDateRange(@Param("start") LocalDate start,
                                              @Param("end") LocalDate end);

    @Query("SELECT r FROM DailyWorkReport r WHERE r.employee.user.role IN :roles " +
           "AND r.reportDate BETWEEN :start AND :end " +
           "ORDER BY r.reportDate DESC, r.employee.firstName ASC")
    List<DailyWorkReport> findByRolesAndDateRange(@Param("roles") List<Role> roles,
                                                   @Param("start") LocalDate start,
                                                   @Param("end") LocalDate end);
}
