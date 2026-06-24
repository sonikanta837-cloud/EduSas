package com.emp.management.repository;

import com.emp.management.entity.InterviewNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterviewNotificationRepository extends JpaRepository<InterviewNotification, Long> {

    List<InterviewNotification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    long countByRecipientIdAndReadFalse(Long recipientId);

    @Modifying
    @Query("UPDATE InterviewNotification n SET n.read = true WHERE n.recipient.id = :recipientId AND n.read = false")
    void markAllReadByRecipientId(Long recipientId);

    @Modifying
    @Query("DELETE FROM InterviewNotification n WHERE n.round.id IN :roundIds")
    void deleteByRoundIdIn(@Param("roundIds") List<Long> roundIds);
}
