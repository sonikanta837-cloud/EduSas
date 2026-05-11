package com.emp.management.config;

import com.emp.management.entity.EmployeeDetails;
import com.emp.management.entity.Role;
import com.emp.management.entity.User;
import com.emp.management.repository.EmployeeDetailsRepository;
import com.emp.management.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final EmployeeDetailsRepository employeeDetailsRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (!userRepository.existsByEmail("admin@company.com")) {
            User admin = User.builder()
                    .email("admin@company.com")
                    .password(passwordEncoder.encode("Admin@1234"))
                    .role(Role.ADMIN)
                    .active(true)
                    .build();
            admin = userRepository.save(admin);

            EmployeeDetails adminDetails = EmployeeDetails.builder()
                    .user(admin)
                    .firstName("System")
                    .lastName("Admin")
                    .department("IT")
                    .position("System Administrator")
                    .hireDate(LocalDate.now())
                    .active(true)
                    .build();
            employeeDetailsRepository.save(adminDetails);

            log.info("Default admin created: admin@company.com / Admin@1234");
        }
    }
}
