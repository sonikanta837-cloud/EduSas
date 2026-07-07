package com.emp.management.dto;

import com.emp.management.entity.InterviewDifficulty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateVideoInterviewRequest {
    private String candidateName;
    private String email;
    private String mobile;
    private String position;
    private String technology;
    private String experience;
    private Integer durationMinutes;
    private Integer numQuestions;
    private InterviewDifficulty difficulty;
}
