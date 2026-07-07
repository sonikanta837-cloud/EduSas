package com.emp.management.dto;

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
public class CandidateInterviewDTO {
    private String candidateName;
    private String position;
    private String technology;
    private Integer durationMinutes;
    private Integer numQuestions;
    private VideoInterviewStatus status;
    private LocalDateTime startedAt;
    private List<CandidateQuestionDTO> questions;
    private List<SaveAnswerRequest> savedAnswers;
}
