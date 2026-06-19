package com.emp.management.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewFeedbackDTO {
    private Long id;
    private Long roundId;
    private Long submittedById;
    private String submittedByName;

    private Integer overallRating;
    private Integer technicalRating;
    private Integer communicationRating;
    private Integer problemSolvingRating;
    private Integer cultureFitRating;

    private String strengths;
    private String weaknesses;
    private String comments;
    private String recommendation;
    private String projectFit;        // YES / MAYBE / NO
    private String suspectedCheating; // YES / MAYBE / NO

    private LocalDateTime submittedAt;
}
