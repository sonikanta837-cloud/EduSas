package com.emp.management.dto;

import com.emp.management.entity.QuestionDifficulty;
import com.emp.management.entity.QuestionStatus;
import com.emp.management.entity.QuestionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewQuestionDTO {
    private Long id;
    private String questionText;
    private String category;
    private String technology;
    private QuestionDifficulty difficulty;
    private QuestionType questionType;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    private String correctAnswer;
    private String expectedKeywords;
    private Integer marks;
    private QuestionStatus status;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
