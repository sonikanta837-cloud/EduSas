package com.emp.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipOutcomeDTO {
    private String status; // COMPLETED | EXTENDED | TERMINATED
    private String finalNotes;
    private LocalDate outcomeDate;
    private LocalDate newEndDate; // used when status = EXTENDED
}
