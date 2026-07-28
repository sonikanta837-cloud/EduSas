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
public class MasterValueCreateRequest {
    // Required for CLIENT / JOB / JOB_TYPE. For PERIOD_END this is ignored —
    // the display value is derived server-side from periodEndDate.
    private String value;

    // Required only for PERIOD_END.
    private LocalDate periodEndDate;
}
