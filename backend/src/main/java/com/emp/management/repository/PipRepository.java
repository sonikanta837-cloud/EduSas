package com.emp.management.repository;

import com.emp.management.entity.PerformanceImprovementPlan;
import com.emp.management.entity.PipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PipRepository extends JpaRepository<PerformanceImprovementPlan, Long> {

    List<PerformanceImprovementPlan> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    List<PerformanceImprovementPlan> findByCreatedByIdOrderByCreatedAtDesc(Long createdById);

    List<PerformanceImprovementPlan> findAllByOrderByCreatedAtDesc();

    List<PerformanceImprovementPlan> findByStatusOrderByCreatedAtDesc(PipStatus status);

    // All PIPs where the employee's direct manager is the given person
    @Query("SELECT p FROM PerformanceImprovementPlan p WHERE p.employee.manager.id = :managerId ORDER BY p.createdAt DESC")
    List<PerformanceImprovementPlan> findByEmployeeManagerId(@Param("managerId") Long managerId);

    // Count goals by status for a PIP — used for stats
    @Query("SELECT COUNT(g) FROM PipGoal g WHERE g.pip.id = :pipId AND g.status = 'ACHIEVED'")
    long countAchievedGoals(@Param("pipId") Long pipId);

    @Query("SELECT COUNT(g) FROM PipGoal g WHERE g.pip.id = :pipId")
    long countTotalGoals(@Param("pipId") Long pipId);

    @Query("SELECT COUNT(r) FROM PipWeeklyReview r WHERE r.pip.id = :pipId")
    long countWeeklyReviews(@Param("pipId") Long pipId);

    @Query("SELECT COUNT(c) FROM PipComment c WHERE c.pip.id = :pipId")
    long countComments(@Param("pipId") Long pipId);

    @Query("SELECT COALESCE(AVG(g.progressPercent), 0) FROM PipGoal g WHERE g.pip.id = :pipId")
    Double avgGoalProgress(@Param("pipId") Long pipId);
}
