package com.emp.management.dto;

import com.emp.management.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class RegisterRequest {
    @NotBlank @Email
    private String email;
    @NotBlank @Size(min = 8, message = "Password must be at least 8 characters long")
    private String password;
    @NotBlank
    private String firstName;
    @NotBlank
    private String lastName;
    @NotNull
    private Role role;

    private String employeeCode;
    @Pattern(regexp = "^$|^[+]?[0-9]{7,15}$", message = "Invalid phone number format")
    private String phone;
    private String personalEmail;
    private String workEmail;
    private String department;
    private String position;
    private String employmentType;
    private String sourceOfHire;
    private LocalDate hireDate;
    private LocalDate dateOfExit;
    @PastOrPresent(message = "Date of birth cannot be in the future")
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
