package com.emp.management.repository;

import com.emp.management.entity.VideoInterview;
import com.emp.management.entity.VideoInterviewQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VideoInterviewQuestionRepository extends JpaRepository<VideoInterviewQuestion, Long> {

    List<VideoInterviewQuestion> findByInterviewOrderByQuestionOrder(VideoInterview interview);

    void deleteByInterview(VideoInterview interview);
}
