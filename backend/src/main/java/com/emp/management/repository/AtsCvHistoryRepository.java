package com.emp.management.repository;

import com.emp.management.entity.AtsCvHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AtsCvHistoryRepository extends JpaRepository<AtsCvHistory, Long> {
    List<AtsCvHistory> findByCandidateIdOrderByReplacedAtDesc(Long candidateId);
}
