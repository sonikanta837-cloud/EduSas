package com.emp.management.repository;

import com.emp.management.entity.FAQ;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FaqRepository extends JpaRepository<FAQ, Long> {
    List<FAQ> findAllByOrderByDisplayOrderAscCreatedAtAsc();
    List<FAQ> findByActiveTrueOrderByDisplayOrderAscCreatedAtAsc();
}
