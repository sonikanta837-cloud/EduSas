package com.emp.management.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
    @Email(message = "Invalid email format")
    private String email;
    @NotBlank(message = "First name is required")
    private String firstName;
    @NotBlank(message = "Last name is required")
    private String lastName;
    private String fullName;
    private String employeeCode;
    @Pattern(regexp = "^$|^[+]?[0-9]{7,15}$", message = "Invalid phone number format")
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
    @DecimalMin(value = "0.0", message = "Salary cannot be negative")
    private BigDecimal salary;

    private String profileImageUrl;
    private String photoUrl;
    private boolean active;
    private String role;
    private String password;
    private Long managerId;
    private String managerName;
    private int subordinateCount;
    private Long assignedHrId;
    private String assignedHrName;

    // Audit
    private String addedBy;
    private String modifiedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
