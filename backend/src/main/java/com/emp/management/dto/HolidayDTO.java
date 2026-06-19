package com.emp.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HolidayDTO {
    private Long id;
    @NotBlank(message = "Holiday name is required")
    private String name;
    @NotNull(message = "Holiday date is required")
    private LocalDate date;
    @NotBlank(message = "Holiday type is required")
    private String holidayType;
    private String location;
    private String department;
    private String description;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
