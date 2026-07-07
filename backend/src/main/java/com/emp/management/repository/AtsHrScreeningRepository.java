package com.emp.management.repository;

import com.emp.management.entity.AtsHrScreening;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AtsHrScreeningRepository extends JpaRepository<AtsHrScreening, Long> {

    Optional<AtsHrScreening> findByCandidateId(Long candidateId);

    /**
     * Bulk-deletes the HR screening record for a candidate directly at the SQL level,
     * bypassing Hibernate's entity lifecycle so the cascade on AtsCandidate.hrScreening
     * cannot re-persist the deleted record. clearAutomatically evicts the session cache
     * so the subsequent candidate save sees a clean state.
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM AtsHrScreening s WHERE s.candidate.id = :candidateId")
    void deleteByCandidateId(@Param("candidateId") Long candidateId);
}
