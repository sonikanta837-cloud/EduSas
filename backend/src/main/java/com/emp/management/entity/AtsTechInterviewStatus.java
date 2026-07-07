package com.emp.management.entity;

public enum AtsTechInterviewStatus {
    PENDING_LINK,         // Assigned to manager, no link generated yet
    LINK_GENERATED,       // Link sent to candidate
    IN_PROGRESS,          // Candidate started the interview
    CANDIDATE_SUBMITTED,  // Candidate submitted answers
    EVALUATED             // Manager submitted final evaluation
}
