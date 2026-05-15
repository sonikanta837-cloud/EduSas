package com.emp.management.service;

import com.emp.management.dto.PerformanceReviewDTO;
import com.emp.management.entity.EmployeeDetails;
import com.emp.management.entity.PerformanceReview;
import com.emp.management.exception.BadRequestException;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.EmployeeDetailsRepository;
import com.emp.management.repository.PerformanceReviewRepository;
import lombok.RequiredArgsConstructor;
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

    public List<PerformanceReviewDTO> getEmployeeReviews(Long employeeId) {
        return reviewRepository.findByEmployeeIdOrderByReviewDateDesc(employeeId).stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    public List<PerformanceReviewDTO> getReviewsByReviewer(Long reviewerId) {
        return reviewRepository.findByReviewerIdOrderByReviewDateDesc(reviewerId).stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    public List<PerformanceReviewDTO> getAllReviews() {
        return reviewRepository.findAllActiveEmployeeReviews().stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    public Double getAverageRating(Long employeeId) {
        return reviewRepository.getAverageRatingByEmployee(employeeId);
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
