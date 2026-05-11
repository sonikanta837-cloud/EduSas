package com.emp.management.repository;

import com.emp.management.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByActive(boolean active);
    long countByActive(boolean active);

    @Query("SELECT DISTINCT c FROM Course c JOIN c.enrollments e WHERE e.employee.id = :employeeId AND c.active = true")
    List<Course> findEnrolledActiveCoursesForEmployee(@Param("employeeId") Long employeeId);
}
