package com.emp.management.repository;

import com.emp.management.entity.VideoInterview;
import com.emp.management.entity.VideoInterviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VideoInterviewRepository extends JpaRepository<VideoInterview, Long> {

    Optional<VideoInterview> findByToken(String token);

    List<VideoInterview> findAllByOrderByCreatedAtDesc();

    List<VideoInterview> findByStatusAndExpiresAtBefore(VideoInterviewStatus status, LocalDateTime cutoff);

    long countByStatus(VideoInterviewStatus status);
}
