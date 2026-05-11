package com.emp.management.repository;

import com.emp.management.entity.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CertificateRepository extends JpaRepository<Certificate, Long> {
    List<Certificate> findByEmployeeId(Long employeeId);
    Optional<Certificate> findByCertificateNumber(String certificateNumber);
    boolean existsByEmployeeIdAndCourseId(Long employeeId, Long courseId);
    Optional<Certificate> findByEmployeeIdAndCourseId(Long employeeId, Long courseId);
}
