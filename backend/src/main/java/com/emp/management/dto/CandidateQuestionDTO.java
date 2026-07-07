package com.emp.management.dto;

import com.emp.management.entity.QuestionDifficulty;
import com.emp.management.entity.QuestionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateQuestionDTO {
    private Long viqId;
    private Long questionId;
    private int questionNumber;
    private String questionText;
    private String category;
    private QuestionDifficulty difficulty;
    private QuestionType questionType;
    private Integer marks;
    private List<McqOption> options;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class McqOption {
        private String letter;
        private String text;
    }
}
