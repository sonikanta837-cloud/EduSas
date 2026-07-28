package com.emp.management.repository;

import com.emp.management.entity.CorrectionStatus;
import com.emp.management.entity.TimesheetCorrectionRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CorrectionRequestRepository extends JpaRepository<TimesheetCorrectionRequest, Long> {

    List<TimesheetCorrectionRequest> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    List<TimesheetCorrectionRequest> findByStatusOrderByCreatedAtDesc(CorrectionStatus status);

    List<TimesheetCorrectionRequest> findByEmployee_Manager_IdAndStatusOrderByCreatedAtDesc(
            Long managerId, CorrectionStatus status);

    boolean existsBySessionIdAndStatus(Long sessionId, CorrectionStatus status);

    // Used to exclude employees with an already-approved correction for a date
    // from the new job-tracking under-hours audit email.
    boolean existsByEmployee_IdAndWorkDateAndStatus(Long employeeId, LocalDate workDate, CorrectionStatus status);
}
