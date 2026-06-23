package com.emp.management.repository;

import com.emp.management.entity.InterviewFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InterviewFeedbackRepository extends JpaRepository<InterviewFeedback, Long> {

    List<InterviewFeedback> findByRoundId(Long roundId);

    List<InterviewFeedback> findByRoundIdIn(List<Long> roundIds);

    Optional<InterviewFeedback> findByRoundIdAndSubmittedById(Long roundId, Long submittedById);

    boolean existsByRoundIdAndSubmittedById(Long roundId, Long submittedById);

    List<InterviewFeedback> findBySubmittedById(Long submittedById);

    @Query("SELECT AVG(f.overallRating) FROM InterviewFeedback f WHERE f.overallRating IS NOT NULL")
    Double getAverageOverallRating();
}
