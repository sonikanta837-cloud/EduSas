package com.emp.management.repository;

import com.emp.management.entity.InterviewRound;
import com.emp.management.entity.RoundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterviewRoundRepository extends JpaRepository<InterviewRound, Long> {

    List<InterviewRound> findByCandidateIdOrderByRoundNumberAsc(Long candidateId);

    @Query("SELECT r FROM InterviewRound r WHERE r.candidate.id IN :candidateIds ORDER BY r.roundNumber ASC")
    List<InterviewRound> findByCandidateIdsOrderByRoundNumberAsc(@Param("candidateIds") List<Long> candidateIds);

    List<InterviewRound> findByInterviewerIdOrderByScheduledAtDesc(Long interviewerId);

    List<InterviewRound> findByInterviewerIdAndStatusOrderByScheduledAtAsc(Long interviewerId, RoundStatus status);

    @Query("SELECT MAX(r.roundNumber) FROM InterviewRound r WHERE r.candidate.id = :candidateId")
    Integer findMaxRoundNumberByCandidateId(Long candidateId);

    List<InterviewRound> findByStatusOrderByScheduledAtAsc(RoundStatus status);

    long countByStatus(RoundStatus status);
}
