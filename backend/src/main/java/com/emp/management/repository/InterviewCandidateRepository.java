package com.emp.management.repository;

import com.emp.management.entity.CandidateStatus;
import com.emp.management.entity.InterviewCandidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterviewCandidateRepository extends JpaRepository<InterviewCandidate, Long> {

    List<InterviewCandidate> findAllByOrderByCreatedAtDesc();

    List<InterviewCandidate> findByStatusOrderByCreatedAtDesc(CandidateStatus status);

    List<InterviewCandidate> findByCreatedByIdOrderByCreatedAtDesc(Long createdById);

    @Query("SELECT COUNT(c) FROM InterviewCandidate c WHERE c.status = :status")
    long countByStatus(CandidateStatus status);
}
