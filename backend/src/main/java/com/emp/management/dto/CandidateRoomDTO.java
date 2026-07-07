package com.emp.management.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateRoomDTO {

    private Long interviewId;
    private String candidateName;
    private String candidateEmail;
    private String appliedProfile;
    private String interviewStatus;
    private Integer durationMinutes;
    private LocalDateTime startedAt;
    private List<CandidateQuestionDTO> questions;
    private List<AnswerStatusDTO> savedAnswers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CandidateQuestionDTO {
        private Long questionId;
        private Integer questionNumber;
        private String questionType;   // TEXT / MCQ
        private String questionText;
        private Integer marks;
        private List<McqOption> options; // null for TEXT questions
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class McqOption {
        private String letter;  // display letter shown to candidate (A/B/C/D in shuffled order)
        private String text;    // option text
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AnswerStatusDTO {
        private Long questionId;
        private String answerText;
        private String selectedOption;  // display letter candidate chose
    }
}
