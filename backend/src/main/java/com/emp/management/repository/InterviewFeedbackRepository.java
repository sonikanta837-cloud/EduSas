package com.emp.management.repository;

import com.emp.management.entity.InterviewFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InterviewFeedbackRepository extends JpaRepository<InterviewFeedback, Long> {

    List<InterviewFeedback> findByRoundId(Long roundId);

    Optional<InterviewFeedback> findByRoundIdAndSubmittedById(Long roundId, Long submittedById);

    boolean existsByRoundIdAndSubmittedById(Long roundId, Long submittedById);

    List<InterviewFeedback> findBySubmittedById(Long submittedById);
}
