package com.emp.management.repository;

import com.emp.management.entity.TimesheetAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TimesheetAuditLogRepository extends JpaRepository<TimesheetAuditLog, Long> {

    List<TimesheetAuditLog> findAllByOrderByActionDateTimeDesc();

    List<TimesheetAuditLog> findByEmployeeIdOrderByActionDateTimeDesc(Long employeeId);
}
