package com.emp.management.repository;

import com.emp.management.entity.AtsTechInterviewQuestion;
import com.emp.management.entity.AtsTechnicalInterview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AtsTechInterviewQuestionRepository extends JpaRepository<AtsTechInterviewQuestion, Long> {

    List<AtsTechInterviewQuestion> findByInterviewOrderByQuestionOrder(AtsTechnicalInterview interview);

    void deleteByInterview(AtsTechnicalInterview interview);
}
