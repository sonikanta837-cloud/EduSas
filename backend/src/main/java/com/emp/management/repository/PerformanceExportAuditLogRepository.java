package com.emp.management.repository;

import com.emp.management.entity.PerformanceExportAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PerformanceExportAuditLogRepository extends JpaRepository<PerformanceExportAuditLog, Long> {
    List<PerformanceExportAuditLog> findTop50ByOrderByCreatedAtDesc();
}
