package com.emp.management.service;

import com.emp.management.entity.InterviewRound;
import com.emp.management.entity.RoundStatus;
import com.emp.management.repository.InterviewRoundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewReminderTask {

    private final InterviewRoundRepository roundRepo;
    private final EmailService             emailService;

    // Runs every minute; finds rounds scheduled 25–35 min from now that haven't been reminded yet.
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void sendPendingReminders() {
        LocalDateTime now  = LocalDateTime.now();
        LocalDateTime from = now.plusMinutes(25);
        LocalDateTime to   = now.plusMinutes(35);

        List<InterviewRound> due = roundRepo.findRoundsDueForReminder(RoundStatus.SCHEDULED, from, to);
        if (due.isEmpty()) return;

        log.info("Interview reminder task: {} round(s) due", due.size());

        for (InterviewRound round : due) {
            try {
                String email         = round.getInterviewer().getUser().getEmail();
                String name          = round.getInterviewer().getFullName();
                String candidateName = round.getCandidate().getName();
                String position      = round.getCandidate().getPosition();
                String roundType    = round.getRoundType().name();

                emailService.sendInterviewReminderEmail(email, name, candidateName, position,
                        roundType, round.getScheduledAt(), round.getLocation());

                round.setReminderSent(true);
                roundRepo.save(round);

            } catch (Exception e) {
                log.error("Failed to process reminder for round {}: {}", round.getId(), e.getMessage());
            }
        }
    }
}
