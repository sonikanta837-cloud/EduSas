package com.emp.management.repository;

import com.emp.management.entity.PipGoal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PipGoalRepository extends JpaRepository<PipGoal, Long> {
    List<PipGoal> findByPipIdOrderByCreatedAtAsc(Long pipId);
}
