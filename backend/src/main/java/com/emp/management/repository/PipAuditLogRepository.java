package com.emp.management.repository;

import com.emp.management.entity.PipAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PipAuditLogRepository extends JpaRepository<PipAuditLog, Long> {
    List<PipAuditLog> findByPipIdOrderByCreatedAtAsc(Long pipId);
}
