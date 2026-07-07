package com.emp.management.repository;

import com.emp.management.entity.AtsTechInterviewAnswer;
import com.emp.management.entity.AtsTechnicalInterview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AtsTechInterviewAnswerRepository extends JpaRepository<AtsTechInterviewAnswer, Long> {

    Optional<AtsTechInterviewAnswer> findByInterviewAndQuestionId(AtsTechnicalInterview interview, Long questionId);

    List<AtsTechInterviewAnswer> findByInterview(AtsTechnicalInterview interview);
}
