package com.emp.management.repository;

import com.emp.management.entity.AtsTechnicalInterview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AtsTechnicalInterviewRepository extends JpaRepository<AtsTechnicalInterview, Long> {

    List<AtsTechnicalInterview> findByCandidateIdOrderByCreatedAtAsc(Long candidateId);

    List<AtsTechnicalInterview> findByInterviewerIdOrderByScheduledAtAsc(Long interviewerId);

    Optional<AtsTechnicalInterview> findByToken(String token);

    boolean existsByToken(String token);
}
