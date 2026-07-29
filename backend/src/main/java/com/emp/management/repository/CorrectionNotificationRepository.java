package com.emp.management.repository;

import com.emp.management.entity.CorrectionNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CorrectionNotificationRepository extends JpaRepository<CorrectionNotification, Long> {

    List<CorrectionNotification> findByRecipientIdAndReadFalseOrderByCreatedAtDesc(Long recipientId);

    long countByRecipientIdAndReadFalse(Long recipientId);

    @Modifying
    @Query("UPDATE CorrectionNotification n SET n.read = true WHERE n.recipient.id = :recipientId AND n.read = false")
    void markAllReadByRecipientId(Long recipientId);
}
