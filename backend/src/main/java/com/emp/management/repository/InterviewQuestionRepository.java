package com.emp.management.repository;

import com.emp.management.entity.InterviewQuestion;
import com.emp.management.entity.QuestionDifficulty;
import com.emp.management.entity.QuestionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, Long> {

    List<InterviewQuestion> findAllByOrderByCreatedAtDesc();

    List<InterviewQuestion> findByStatusOrderByCreatedAtDesc(QuestionStatus status);

    long countByStatus(QuestionStatus status);

    @Query("SELECT DISTINCT q.technology FROM InterviewQuestion q WHERE q.technology IS NOT NULL AND q.technology <> '' ORDER BY q.technology")
    List<String> findDistinctTechnologies();

    @Query("SELECT DISTINCT q.category FROM InterviewQuestion q WHERE q.category IS NOT NULL AND q.category <> '' ORDER BY q.category")
    List<String> findDistinctCategories();

    long countByTechnologyIgnoreCaseAndStatus(String technology, QuestionStatus status);

    /** Random questions for MIXED difficulty — all active questions for a technology. */
    @Query(value = "SELECT * FROM interview_questions WHERE status = 'ACTIVE' AND LOWER(technology) = LOWER(:tech) ORDER BY RAND() LIMIT :cnt",
           nativeQuery = true)
    List<InterviewQuestion> findRandomByTechnology(@Param("tech") String technology, @Param("cnt") int count);

    /** Random questions filtered by specific difficulty. */
    @Query(value = "SELECT * FROM interview_questions WHERE status = 'ACTIVE' AND LOWER(technology) = LOWER(:tech) AND difficulty = :diff ORDER BY RAND() LIMIT :cnt",
           nativeQuery = true)
    List<InterviewQuestion> findRandomByTechnologyAndDifficulty(@Param("tech") String technology, @Param("diff") String difficulty, @Param("cnt") int count);

    /** Random active questions across all technologies — last-resort fallback so results are never a fixed, deterministic set. */
    @Query(value = "SELECT * FROM interview_questions WHERE status = 'ACTIVE' ORDER BY RAND() LIMIT :cnt",
           nativeQuery = true)
    List<InterviewQuestion> findRandomActive(@Param("cnt") int count);
}
