package com.emp.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveAnswerRequest {
    private Long questionId;
    private String answerText;
    private String selectedOption;
    private Boolean isMarked;
}
