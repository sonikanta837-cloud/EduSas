package com.emp.management.repository;

import com.emp.management.entity.PipComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PipCommentRepository extends JpaRepository<PipComment, Long> {
    List<PipComment> findByPipIdOrderByCreatedAtAsc(Long pipId);
    long countByPipId(Long pipId);
}
