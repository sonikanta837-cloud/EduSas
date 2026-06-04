package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "employee_details")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

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
    private String gender;
    private String maritalStatus;

    // Identity
    private String aadharNumber;
    private String panNumber;
    private String uanNumber;

    // Address
    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(columnDefinition = "TEXT")
    private String presentAddress;

    @Column(columnDefinition = "TEXT")
    private String permanentAddress;

    // Work
    private String seatingLocation;
    private String currentExperience;
    private String totalExperience;
    private BigDecimal salary;

    @Column(columnDefinition = "TEXT")
    private String skills;

    @Column(columnDefinition = "TEXT")
    private String experience;

    private String profileImageUrl;
    private String photoUrl;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private EmployeeDetails manager;

    @OneToMany(mappedBy = "manager", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    private List<EmployeeDetails> subordinates = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_hr_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private EmployeeDetails assignedHr;

    // Audit
    private String addedBy;
    private String modifiedBy;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
