package com.emp.management.dto;

import com.emp.management.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class RegisterRequest {
    @NotBlank @Email
    private String email;
    @NotBlank
    private String password;
    @NotBlank
    private String firstName;
    @NotBlank
    private String lastName;
    @NotNull
    private Role role;

    private String employeeCode;
    private String phone;
    private String personalEmail;
    private String workEmail;
    private String department;
    private String position;
    private String employmentType;
    private String sourceOfHire;
    private LocalDate hireDate;
    private LocalDate dateOfExit;
    private LocalDate dateOfBirth;
    private String gender;
    private String maritalStatus;
    private String aadharNumber;
    private String panNumber;
    private String uanNumber;
    private String address;
    private String presentAddress;
    private String permanentAddress;
    private String seatingLocation;
    private String currentExperience;
    private String totalExperience;
    private String skills;
    private String experience;
    private String photoUrl;
    private Long managerId;
}
