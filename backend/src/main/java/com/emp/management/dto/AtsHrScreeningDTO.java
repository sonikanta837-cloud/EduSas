package com.emp.management.dto;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AtsHrScreeningDTO {

    private Long id;
    private Long candidateId;

    private String communicationSkills;
    private String currentCompany;
    private String currentCtc;
    private String expectedCtc;
    private String noticePeriod;
    private String totalExperience;
    private String relevantExperience;
    private String preferredLocation;
    private String availability;
    private String reasonForChange;
    private String currentRoleResponsibilities;
    private String workBase;
    private LocalDate screeningDate;
    private String hrComments;
    private String rejectionReason;

    // PENDING / SUITABLE / NOT_SUITABLE
    private String decision;

    private Long conductedById;
    private String conductedByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Used only when submitting SUITABLE decision — tells service to assign technical interview
    private Long interviewerId;
    private String interviewerName;
    private LocalDateTime scheduledAt;
}
