package com.emp.management.repository;

import com.emp.management.entity.EmployeeDetails;
import com.emp.management.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeDetailsRepository extends JpaRepository<EmployeeDetails, Long> {
    Optional<EmployeeDetails> findByUser(User user);
    Optional<EmployeeDetails> findByUserId(Long userId);
    Optional<EmployeeDetails> findByUserEmail(String email);
    List<EmployeeDetails> findByManagerId(Long managerId);
    List<EmployeeDetails> findByDepartment(String department);
    List<EmployeeDetails> findByActive(boolean active);

    List<EmployeeDetails> findByManagerIsNullAndActiveTrue();

    @Query("SELECT e FROM EmployeeDetails e WHERE (LOWER(e.firstName) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(e.lastName) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(e.department) LIKE LOWER(CONCAT('%', :q, '%')))")
    List<EmployeeDetails> searchEmployees(@Param("q") String query);

    long countByActive(boolean active);
    long countByManagerIsNotNull();

    @Query("SELECT e FROM EmployeeDetails e WHERE e.employeeCode IS NULL OR e.employeeCode = ''")
    List<EmployeeDetails> findByEmployeeCodeMissing();

    @Query("SELECT e.employeeCode FROM EmployeeDetails e WHERE e.employeeCode IS NOT NULL AND e.employeeCode <> ''")
    List<String> findAllEmployeeCodes();
}
