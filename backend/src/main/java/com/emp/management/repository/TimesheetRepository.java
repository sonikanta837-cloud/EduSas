package com.emp.management.repository;

import com.emp.management.entity.Timesheet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

// Only used by the missing-timesheet-entry audit now (attendance moved to Job
// Time Tracking / JobDailySummary) — holds the missingAlertSent/manualOverride
// guard flags for that unrelated audit.
@Repository
public interface TimesheetRepository extends JpaRepository<Timesheet, Long> {
    Optional<Timesheet> findByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);
}
