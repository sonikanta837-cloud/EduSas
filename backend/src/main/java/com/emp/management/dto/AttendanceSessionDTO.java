package com.emp.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceSessionDTO {
    private Long id;
    private Long employeeId;
    private LocalDate workDate;
    private LocalTime loginTime;
    private LocalTime logoutTime;
    private Double sessionHours;
    private String status;
    private LocalTime firstLoginTime;
}
