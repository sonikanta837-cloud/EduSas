package com.emp.management.repository;

import com.emp.management.entity.VideoInterview;
import com.emp.management.entity.VideoInterviewAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VideoInterviewAnswerRepository extends JpaRepository<VideoInterviewAnswer, Long> {

    List<VideoInterviewAnswer> findByInterview(VideoInterview interview);

    Optional<VideoInterviewAnswer> findByInterviewAndQuestionId(VideoInterview interview, Long questionId);

    void deleteByInterview(VideoInterview interview);
}
