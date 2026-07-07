package com.emp.management.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagerRoomDTO {

    private Long interviewId;
    private String token;
    private String candidateName;
    private String candidateEmail;
    private String appliedProfile;
    private String interviewStatus;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Integer durationMinutes;
    private Integer score;
    private Integer totalMarks;
    private Integer violationCount;
    private String videoUrl;
    private String offerSdp;  // WebRTC offer from candidate

    private List<QuestionWithAnswer> questionsWithAnswers;
    private List<ViolationRecord> violations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionWithAnswer {
        private Long questionId;
        private Integer questionNumber;
        private String questionText;
        private String questionType;
        private Integer marks;
        private String correctAnswer;   // original correct letter for MCQ
        private List<String> options;   // original A/B/C/D options
        private String selectedOption;  // candidate's selected original letter
        private String answerText;      // candidate's text answer
        private Boolean correct;        // null for TEXT (no auto-grade)
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ViolationRecord {
        private String violationType;
        private String description;
        private Integer violationNumber;
        private LocalDateTime occurredAt;
    }
}
