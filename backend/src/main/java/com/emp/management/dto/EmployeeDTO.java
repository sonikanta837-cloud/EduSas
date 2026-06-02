package com.emp.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeDTO {
    private Long id;
    private Long userId;
    private String email;
    private String firstName;
    private String lastName;
    private String fullName;
    private String employeeCode;
    private String phone;
    private String personalEmail;
    private String workEmail;
    private String department;
    private String position;

    // Employment
    private String employmentType;
    private String sourceOfHire;
    private LocalDate hireDate;
    private LocalDate dateOfExit;

    // Personal
    private LocalDate dateOfBirth;
    private Integer age;
    private String gender;
    private String maritalStatus;

    // Identity
    private String aadharNumber;
    private String panNumber;
    private String uanNumber;

    // Address
    private String address;
    private String presentAddress;
    private String permanentAddress;

    // Work
    private String seatingLocation;
    private String currentExperience;
    private String totalExperience;
    private String skills;
    private String experience;
    private BigDecimal salary;

    private String profileImageUrl;
    private String photoUrl;
    private boolean active;
    private String role;
    private String password;
    private Long managerId;
    private String managerName;
    private int subordinateCount;

    // Audit
    private String addedBy;
    private String modifiedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
