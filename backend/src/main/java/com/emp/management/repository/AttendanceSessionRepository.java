package com.emp.management.repository;

import com.emp.management.entity.AttendanceSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, Long> {

    List<AttendanceSession> findByEmployeeIdAndWorkDateOrderByLoginTimeAsc(Long empId, LocalDate date);

    List<AttendanceSession> findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAscLoginTimeAsc(Long empId, LocalDate start, LocalDate end);

    Optional<AttendanceSession> findTopByEmployeeIdAndWorkDateAndLogoutTimeIsNullOrderByLoginTimeDesc(Long empId, LocalDate date);
}
