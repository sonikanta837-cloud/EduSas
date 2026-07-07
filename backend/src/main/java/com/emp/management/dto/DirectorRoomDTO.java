package com.emp.management.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectorRoomDTO {

    private Long   finalRoundId;
    private String token;
    private String candidateName;
    private String candidateEmail;
    private String appliedProfile;
    private String interviewStatus;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime evaluatedAt;
    private String videoUrl;

    // WebRTC — offer from candidate (director reads this to create answer)
    private String offerSdp;

    // Existing evaluation (if already submitted)
    private Integer overallRating;
    private Integer communicationRating;
    private Integer cultureFitRating;
    private String  salaryDiscussion;
    private String  expectedCtc;
    private String  offeredCtc;
    private String  noticePeriod;
    private String  directorRemarks;
    private String  finalDecision;
}
