package com.emp.management.repository;

import com.emp.management.entity.AtsCandidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AtsCandidateRepository extends JpaRepository<AtsCandidate, Long> {

    List<AtsCandidate> findAllByOrderByCreatedAtDesc();

    @Query("SELECT c FROM AtsCandidate c ORDER BY COALESCE(c.resumeUploadedAt, c.createdAt) DESC")
    List<AtsCandidate> findAllOrderByCvUploadDateDesc();

    List<AtsCandidate> findByStatusOrderByCreatedAtDesc(String status);

    long countByStatus(String status);

    Optional<AtsCandidate> findByCandidateId(String candidateId);

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(c.candidateId, 5) AS long)), 10000) FROM AtsCandidate c")
    Long findMaxCandidateSequence();

    @Query("SELECT c FROM AtsCandidate c WHERE LOWER(c.email) = LOWER(:email)")
    Optional<AtsCandidate> findByEmailIgnoreCase(@Param("email") String email);

    Optional<AtsCandidate> findByPhone(String phone);

    @Query("SELECT c FROM AtsCandidate c WHERE LOWER(c.name) = LOWER(:name) ORDER BY c.createdAt DESC")
    List<AtsCandidate> findByNameIgnoreCase(@Param("name") String name);
}
