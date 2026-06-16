package com.emp.management.repository;

import com.emp.management.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    @Query("SELECT h FROM Holiday h ORDER BY h.date ASC")
    List<Holiday> findAllOrderByDate();

    @Query("SELECT h FROM Holiday h WHERE YEAR(h.date) = :year ORDER BY h.date ASC")
    List<Holiday> findByYear(@Param("year") int year);

    @Query("SELECT h FROM Holiday h WHERE h.active = true AND h.date BETWEEN :start AND :end " +
           "AND (h.location IS NULL OR h.location = '' OR h.location = 'ALL' OR h.location = :location) " +
           "AND (h.department IS NULL OR h.department = :department) ORDER BY h.date ASC")
    List<Holiday> findApplicable(@Param("location") String location,
                                  @Param("department") String department,
                                  @Param("start") LocalDate start,
                                  @Param("end") LocalDate end);

    @Query("SELECT h FROM Holiday h WHERE h.active = true " +
           "AND (h.location IS NULL OR h.location = '' OR h.location = 'ALL' OR h.location = :location) " +
           "AND (h.department IS NULL OR h.department = :department) " +
           "AND YEAR(h.date) = :year ORDER BY h.date ASC")
    List<Holiday> findActiveByLocationAndYear(@Param("location") String location,
                                               @Param("department") String department,
                                               @Param("year") int year);

    @Query("SELECT DISTINCT YEAR(h.date) FROM Holiday h ORDER BY YEAR(h.date) DESC")
    List<Integer> findDistinctYears();

    boolean existsByDateAndLocation(LocalDate date, String location);
}
