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
           "AND (h.location = 'ALL' OR h.location = :location) ORDER BY h.date ASC")
    List<Holiday> findApplicable(@Param("location") String location,
                                  @Param("start") LocalDate start,
                                  @Param("end") LocalDate end);

    @Query("SELECT h FROM Holiday h WHERE h.active = true " +
           "AND (h.location = 'ALL' OR h.location = :location) " +
           "AND YEAR(h.date) = :year ORDER BY h.date ASC")
    List<Holiday> findActiveByLocationAndYear(@Param("location") String location,
                                               @Param("year") int year);

    @Query("SELECT DISTINCT YEAR(h.date) FROM Holiday h ORDER BY YEAR(h.date) DESC")
    List<Integer> findDistinctYears();

    boolean existsByDateAndLocation(LocalDate date, String location);
}
