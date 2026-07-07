package com.emp.management.dto;

import com.emp.management.entity.InterviewDifficulty;
import com.emp.management.entity.VideoInterviewStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoInterviewDTO {
    private Long id;
    private String candidateName;
    private String email;
    private String mobile;
    private String position;
    private String technology;
    private String experience;
    private Integer durationMinutes;
    private Integer numQuestions;
    private InterviewDifficulty difficulty;
    private String token;
    private String interviewLink;
    private VideoInterviewStatus status;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;
    private LocalDateTime expiresAt;
    private String videoUrl;
    private Integer score;
    private Integer totalMarks;
    private Integer violationCount;
    private List<VideoAnswerDTO> answers;
    private List<VideoViolationDTO> violations;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class VideoAnswerDTO {
        private Long questionId;
        private String questionText;
        private String questionType;
        private String answerText;
        private String selectedOption;
        private Boolean isMarked;
        private String correctAnswer;
        private Integer marks;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class VideoViolationDTO {
        private Long id;
        private String violationType;
        private String browserEvent;
        private String description;
        private Integer violationNumber;
        private LocalDateTime occurredAt;
    }
}
