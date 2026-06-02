package com.emp.management.repository;

import com.emp.management.entity.CourseNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseNotificationRepository extends JpaRepository<CourseNotification, Long> {

    List<CourseNotification> findByEmployeeIdAndReadFalseOrderByCreatedAtDesc(Long employeeId);

    long countByEmployeeIdAndReadFalse(Long employeeId);

    @Modifying
    @Query("UPDATE CourseNotification n SET n.read = true WHERE n.employee.id = :employeeId AND n.read = false")
    void markAllReadByEmployeeId(@Param("employeeId") Long employeeId);
}
