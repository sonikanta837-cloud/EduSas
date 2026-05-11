package com.emp.management.repository;

import com.emp.management.entity.Leave;
import com.emp.management.entity.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LeaveRepository extends JpaRepository<Leave, Long> {
    List<Leave> findByEmployeeIdOrderByAppliedAtDesc(Long employeeId);

    // PENDING first, then newest appliedAt — used for admin "all leaves" view
    @Query("SELECT l FROM Leave l ORDER BY CASE WHEN l.status = 'PENDING' THEN 0 ELSE 1 END ASC, l.appliedAt DESC")
    List<Leave> findAllOrderByPendingFirst();

    @Query("SELECT l FROM Leave l WHERE l.status = :status ORDER BY l.appliedAt DESC")
    List<Leave> findByStatus(@Param("status") LeaveStatus status);

    // Overlap check for new leave
    @Query("SELECT COUNT(l) > 0 FROM Leave l " +
           "WHERE l.employee.id = :employeeId " +
           "AND l.status IN :statuses " +
           "AND l.startDate <= :endDate AND l.endDate >= :startDate")
    boolean existsOverlappingLeave(@Param("employeeId") Long employeeId,
                                   @Param("startDate") LocalDate startDate,
                                   @Param("endDate") LocalDate endDate,
                                   @Param("statuses") List<LeaveStatus> statuses);

    // Overlap check when editing — excludes the leave being updated
    @Query("SELECT COUNT(l) > 0 FROM Leave l " +
           "WHERE l.employee.id = :employeeId " +
           "AND l.id <> :excludeId " +
           "AND l.status IN :statuses " +
           "AND l.startDate <= :endDate AND l.endDate >= :startDate")
    boolean existsOverlappingLeaveExcluding(@Param("employeeId") Long employeeId,
                                            @Param("startDate") LocalDate startDate,
                                            @Param("endDate") LocalDate endDate,
                                            @Param("statuses") List<LeaveStatus> statuses,
                                            @Param("excludeId") Long excludeId);

    @Query("SELECT l FROM Leave l WHERE l.employee.manager.id = :managerId ORDER BY l.appliedAt DESC")
    List<Leave> findByManagerId(@Param("managerId") Long managerId);

    @Query("SELECT l FROM Leave l WHERE l.employee.manager.id = :managerId AND l.status = :status ORDER BY l.appliedAt DESC")
    List<Leave> findByManagerIdAndStatus(@Param("managerId") Long managerId, @Param("status") LeaveStatus status);

    long countByStatus(LeaveStatus status);
    long countByEmployeeIdAndStatus(Long employeeId, LeaveStatus status);
}
