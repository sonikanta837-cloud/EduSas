package com.emp.management.repository;

import com.emp.management.entity.Enrollment;
import com.emp.management.entity.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    List<Enrollment> findByEmployeeId(Long employeeId);
    List<Enrollment> findByCourseId(Long courseId);
    Optional<Enrollment> findByEmployeeIdAndCourseId(Long employeeId, Long courseId);
    boolean existsByEmployeeIdAndCourseId(Long employeeId, Long courseId);
    long countByCourseIdAndStatus(Long courseId, EnrollmentStatus status);

    @Query("SELECT COUNT(e) * 100.0 / NULLIF((SELECT COUNT(e2) FROM Enrollment e2), 0) FROM Enrollment e WHERE e.status = 'COMPLETED'")
    Double getOverallCompletionPercentage();
}
