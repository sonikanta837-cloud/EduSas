package com.emp.management.repository;

import com.emp.management.entity.AnnouncementView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Repository
public interface AnnouncementViewRepository extends JpaRepository<AnnouncementView, Long> {

    boolean existsByAnnouncementIdAndEmployeeId(Long announcementId, Long employeeId);

    long countByAnnouncementId(Long announcementId);

    List<AnnouncementView> findByAnnouncementId(Long announcementId);

    @Query("SELECT v.announcement.id FROM AnnouncementView v WHERE v.employee.id = :employeeId")
    Set<Long> findReadAnnouncementIdsByEmployeeId(@Param("employeeId") Long employeeId);

    @Query("SELECT COUNT(DISTINCT a.id) FROM Announcement a WHERE a.archived = false " +
           "AND a.authorName <> :username " +
           "AND a.createdAt >= :since " +
           "AND a.id NOT IN (SELECT v.announcement.id FROM AnnouncementView v WHERE v.employee.id = :employeeId)")
    long countUnreadByEmployeeId(@Param("employeeId") Long employeeId,
                                  @Param("username") String username,
                                  @Param("since") LocalDateTime since);

    @Query("SELECT v.announcement.id, COUNT(v) FROM AnnouncementView v GROUP BY v.announcement.id")
    List<Object[]> countGroupedByAnnouncement();
}
