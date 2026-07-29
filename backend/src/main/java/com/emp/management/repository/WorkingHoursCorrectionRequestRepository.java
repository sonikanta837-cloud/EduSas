package com.emp.management.repository;

import com.emp.management.entity.CorrectionRequestStatus;
import com.emp.management.entity.WorkingHoursCorrectionRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkingHoursCorrectionRequestRepository extends JpaRepository<WorkingHoursCorrectionRequest, Long> {

    Optional<WorkingHoursCorrectionRequest> findTopByEmployeeIdAndWorkDateOrderBySubmittedAtDesc(
            Long employeeId, LocalDate workDate);

    List<WorkingHoursCorrectionRequest> findByEmployeeIdOrderBySubmittedAtDesc(Long employeeId);

    List<WorkingHoursCorrectionRequest> findByStatusOrderBySubmittedAtAsc(CorrectionRequestStatus status);

    @Query("SELECT r FROM WorkingHoursCorrectionRequest r WHERE r.employee.manager.id = :managerId " +
           "AND r.status = 'PENDING' ORDER BY r.submittedAt ASC")
    List<WorkingHoursCorrectionRequest> findPendingForManager(@Param("managerId") Long managerId);

    @Query("SELECT r FROM WorkingHoursCorrectionRequest r WHERE r.employee.manager.id = :managerId " +
           "ORDER BY r.submittedAt DESC")
    List<WorkingHoursCorrectionRequest> findAllForManager(@Param("managerId") Long managerId);

    @Query("SELECT r FROM WorkingHoursCorrectionRequest r ORDER BY " +
           "CASE WHEN r.status = 'PENDING' THEN 0 ELSE 1 END ASC, r.submittedAt DESC")
    List<WorkingHoursCorrectionRequest> findAllOrderByPendingFirst();

    boolean existsByEmployeeIdAndWorkDateAndStatus(Long employeeId, LocalDate workDate, CorrectionRequestStatus status);
}
