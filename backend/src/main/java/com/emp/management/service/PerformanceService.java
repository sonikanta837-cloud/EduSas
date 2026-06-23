package com.emp.management.service;

import com.emp.management.dto.PerformanceReviewDTO;
import com.emp.management.entity.EmployeeDetails;
import com.emp.management.entity.PerformanceReview;
import com.emp.management.entity.Role;
import com.emp.management.exception.BadRequestException;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.EmployeeDetailsRepository;
import com.emp.management.repository.PerformanceReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PerformanceService {

    private final PerformanceReviewRepository reviewRepository;
    private final EmployeeDetailsRepository employeeDetailsRepository;

    @Transactional
    public PerformanceReviewDTO createReview(PerformanceReviewDTO dto) {
        if (dto.getRating() < 1 || dto.getRating() > 5) {
            throw new BadRequestException("Rating must be between 1 and 5");
        }

        EmployeeDetails employee = findEmployee(dto.getEmployeeId());
        EmployeeDetails reviewer = findEmployee(dto.getReviewerId());

        PerformanceReview review = PerformanceReview.builder()
                .employee(employee)
                .reviewer(reviewer)
                .rating(dto.getRating())
                .comments(dto.getComments())
                .strengths(dto.getStrengths())
                .areasOfImprovement(dto.getAreasOfImprovement())
                .reviewDate(dto.getReviewDate())
                .reviewPeriod(dto.getReviewPeriod())
                .build();

        return toDTO(reviewRepository.save(review));
    }

    public List<PerformanceReviewDTO> getEmployeeReviews(Long employeeId, String callerEmail) {
        requireSelfOrPrivileged(employeeId, callerEmail);
        return reviewRepository.findByEmployeeIdOrderByReviewDateDesc(employeeId).stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    public List<PerformanceReviewDTO> getReviewsByReviewer(Long reviewerId, String callerEmail) {
        requireSelfOrPrivileged(reviewerId, callerEmail);
        return reviewRepository.findByReviewerIdOrderByReviewDateDesc(reviewerId).stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    public List<PerformanceReviewDTO> getAllReviews() {
        return reviewRepository.findAllActiveEmployeeReviews().stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    public Double getAverageRating(Long employeeId, String callerEmail) {
        requireSelfOrPrivileged(employeeId, callerEmail);
        return reviewRepository.getAverageRatingByEmployee(employeeId);
    }

    @Transactional
    public PerformanceReviewDTO updateReview(Long id, PerformanceReviewDTO dto) {
        if (dto.getRating() < 1 || dto.getRating() > 5) {
            throw new BadRequestException("Rating must be between 1 and 5");
        }
        PerformanceReview review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PerformanceReview", id));
        review.setRating(dto.getRating());
        review.setComments(dto.getComments());
        review.setStrengths(dto.getStrengths());
        review.setAreasOfImprovement(dto.getAreasOfImprovement());
        review.setReviewDate(dto.getReviewDate());
        review.setReviewPeriod(dto.getReviewPeriod());
        return toDTO(reviewRepository.save(review));
    }

    @Transactional
    public void deleteReview(Long id) {
        PerformanceReview review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PerformanceReview", id));
        reviewRepository.delete(review);
    }

    private void requireSelfOrPrivileged(Long targetId, String callerEmail) {
        EmployeeDetails caller = employeeDetailsRepository.findByUserEmail(callerEmail).orElse(null);
        if (caller == null) return;
        Role role = caller.getUser() != null ? caller.getUser().getRole() : null;
        boolean privileged = role == Role.ADMIN || role == Role.DIRECTOR || role == Role.HR
                          || role == Role.MANAGER || role == Role.ASSISTANT_MANAGER;
        if (!privileged && !caller.getId().equals(targetId)) {
            throw new AccessDeniedException("Access denied");
        }
    }

    private EmployeeDetails findEmployee(Long id) {
        return employeeDetailsRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", id));
    }

    private PerformanceReviewDTO toDTO(PerformanceReview r) {
        return PerformanceReviewDTO.builder()
                .id(r.getId())
                .employeeId(r.getEmployee().getId())
                .employeeName(r.getEmployee().getFullName())
                .reviewerId(r.getReviewer().getId())
                .reviewerName(r.getReviewer().getFullName())
                .reviewerRole(r.getReviewer().getUser() != null ? r.getReviewer().getUser().getRole().name() : null)
                .rating(r.getRating())
                .comments(r.getComments())
                .strengths(r.getStrengths())
                .areasOfImprovement(r.getAreasOfImprovement())
                .reviewDate(r.getReviewDate())
                .reviewPeriod(r.getReviewPeriod())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
