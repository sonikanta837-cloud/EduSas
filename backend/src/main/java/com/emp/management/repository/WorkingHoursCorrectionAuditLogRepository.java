package com.emp.management.repository;

import com.emp.management.entity.WorkingHoursCorrectionAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkingHoursCorrectionAuditLogRepository extends JpaRepository<WorkingHoursCorrectionAuditLog, Long> {

    List<WorkingHoursCorrectionAuditLog> findByCorrectionRequestIdOrderByCreatedAtAsc(Long correctionRequestId);
}
