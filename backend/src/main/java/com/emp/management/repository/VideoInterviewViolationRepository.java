package com.emp.management.repository;

import com.emp.management.entity.VideoInterview;
import com.emp.management.entity.VideoInterviewViolation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VideoInterviewViolationRepository extends JpaRepository<VideoInterviewViolation, Long> {

    List<VideoInterviewViolation> findByInterviewOrderByOccurredAtAsc(VideoInterview interview);

    long countByInterview(VideoInterview interview);

    void deleteByInterview(VideoInterview interview);
}
