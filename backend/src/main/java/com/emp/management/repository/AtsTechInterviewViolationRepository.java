package com.emp.management.repository;

import com.emp.management.entity.AtsTechInterviewViolation;
import com.emp.management.entity.AtsTechnicalInterview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AtsTechInterviewViolationRepository extends JpaRepository<AtsTechInterviewViolation, Long> {

    List<AtsTechInterviewViolation> findByInterviewOrderByOccurredAtAsc(AtsTechnicalInterview interview);

    long countByInterview(AtsTechnicalInterview interview);
}
