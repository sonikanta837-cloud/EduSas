package com.emp.management.repository;

import com.emp.management.entity.TimesheetEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TimesheetEntryRepository extends JpaRepository<TimesheetEntry, Long> {
    List<TimesheetEntry> findByEmployeeIdAndDateBetweenOrderByDateAsc(Long empId, LocalDate start, LocalDate end);
    void deleteByEmployeeIdAndProjectNameAndTaskName(Long empId, String projectName, String taskName);
    void deleteByEmployeeIdAndProjectNameAndDateBetween(Long empId, String projectName, LocalDate start, LocalDate end);
    boolean existsByEmployeeIdAndProjectNameAndDate(Long empId, String projectName, LocalDate date);
    boolean existsByEmployeeIdAndDate(Long empId, LocalDate date);

    @Query("SELECT e FROM TimesheetEntry e JOIN FETCH e.employee emp " +
           "WHERE e.date BETWEEN :start AND :end " +
           "ORDER BY e.date ASC, emp.lastName ASC, emp.firstName ASC")
    List<TimesheetEntry> findAllForWorkReport(@Param("start") LocalDate start,
                                              @Param("end")   LocalDate end);
}
