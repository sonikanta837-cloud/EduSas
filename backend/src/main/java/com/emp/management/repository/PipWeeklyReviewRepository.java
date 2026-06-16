package com.emp.management.repository;

import com.emp.management.entity.PipWeeklyReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PipWeeklyReviewRepository extends JpaRepository<PipWeeklyReview, Long> {
    List<PipWeeklyReview> findByPipIdOrderByWeekNumberAsc(Long pipId);
    long countByPipId(Long pipId);
}
