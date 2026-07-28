package com.emp.management.repository;

import com.emp.management.entity.JobSessionBreak;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobSessionBreakRepository extends JpaRepository<JobSessionBreak, Long> {

    List<JobSessionBreak> findByJobWorkSessionIdOrderByBreakStartTimeAsc(Long jobWorkSessionId);

    // Used to roll up a day's total break time (Attendance "Break Time") across
    // all of an employee's job sessions for that day.
    List<JobSessionBreak> findByJobWorkSessionIdIn(List<Long> jobWorkSessionIds);

    Optional<JobSessionBreak> findByJobWorkSessionIdAndBreakEndTimeIsNull(Long jobWorkSessionId);

    // Still-open breaks that haven't triggered the duration alert yet — polled by
    // the active-break monitor to find candidates past the 60-minute threshold.
    List<JobSessionBreak> findByBreakEndTimeIsNullAndAlertSentFalse();
}
