package com.emp.management.repository;

import com.emp.management.entity.Timesheet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TimesheetRepository extends JpaRepository<Timesheet, Long> {
    Optional<Timesheet> findByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);
    List<Timesheet> findByEmployeeIdOrderByWorkDateDesc(Long employeeId);
    List<Timesheet> findByWorkDate(LocalDate workDate);

    @Query("SELECT t FROM Timesheet t WHERE t.employee.id = :empId AND t.workDate BETWEEN :start AND :end ORDER BY t.workDate DESC")
    List<Timesheet> findByEmployeeIdAndDateRange(@Param("empId") Long empId,
                                                  @Param("start") LocalDate start,
                                                  @Param("end") LocalDate end);

    @Query("SELECT SUM(t.workingHours) FROM Timesheet t WHERE t.workDate = :date")
    Double getTotalWorkHoursByDate(@Param("date") LocalDate date);

}
