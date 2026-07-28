package com.emp.management.repository;

import com.emp.management.entity.UnderHoursAlertLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UnderHoursAlertLogRepository extends JpaRepository<UnderHoursAlertLog, Long> {

    List<UnderHoursAlertLog> findAllByOrderBySentAtDesc();

    List<UnderHoursAlertLog> findByEmployeeIdOrderByWorkDateDesc(Long employeeId);
}
