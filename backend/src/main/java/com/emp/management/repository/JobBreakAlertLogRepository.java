package com.emp.management.repository;

import com.emp.management.entity.JobBreakAlertLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobBreakAlertLogRepository extends JpaRepository<JobBreakAlertLog, Long> {

    List<JobBreakAlertLog> findAllByOrderBySentAtDesc();

    List<JobBreakAlertLog> findByEmployeeIdOrderBySentAtDesc(Long employeeId);
}
