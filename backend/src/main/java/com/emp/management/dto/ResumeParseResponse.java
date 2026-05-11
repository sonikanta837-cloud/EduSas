package com.emp.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeParseResponse {
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String skills;
    private String experience;
    // department, position, managerId intentionally omitted — admin fills these
}
