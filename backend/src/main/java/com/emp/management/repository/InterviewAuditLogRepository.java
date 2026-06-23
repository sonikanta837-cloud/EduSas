package com.emp.management.repository;

import com.emp.management.entity.InterviewAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterviewAuditLogRepository extends JpaRepository<InterviewAuditLog, Long> {

    List<InterviewAuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, Long entityId);

    List<InterviewAuditLog> findAllByOrderByCreatedAtDesc();
}
