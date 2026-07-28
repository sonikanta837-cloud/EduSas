package com.emp.management.repository;

import com.emp.management.entity.AtsFinalInterviewStatus;
import com.emp.management.entity.AtsFinalRound;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AtsFinalRoundRepository extends JpaRepository<AtsFinalRound, Long> {

    Optional<AtsFinalRound> findByCandidateId(Long candidateId);
    Optional<AtsFinalRound> findByToken(String token);
    boolean existsByToken(String token);
    List<AtsFinalRound> findByConductedByIdOrderByCreatedAtDesc(Long conductedById);
    boolean existsByConductedByIdAndInterviewStatusNot(Long conductedById, AtsFinalInterviewStatus excludedStatus);
}
