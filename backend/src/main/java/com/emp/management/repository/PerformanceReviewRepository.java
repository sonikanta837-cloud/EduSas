package com.emp.management.repository;

import com.emp.management.entity.PerformanceReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PerformanceReviewRepository extends JpaRepository<PerformanceReview, Long> {
    List<PerformanceReview> findByEmployeeIdOrderByReviewDateDesc(Long employeeId);
    List<PerformanceReview> findByReviewerIdOrderByReviewDateDesc(Long reviewerId);

    @Query("SELECT AVG(p.rating) FROM PerformanceReview p WHERE p.employee.id = :employeeId")
    Double getAverageRatingByEmployee(@Param("employeeId") Long employeeId);

    @Query("SELECT p FROM PerformanceReview p WHERE p.employee.active = true ORDER BY p.reviewDate DESC")
    List<PerformanceReview> findAllActiveEmployeeReviews();
}
