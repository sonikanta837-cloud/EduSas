package com.emp.management.service;

import com.emp.management.dto.PerformanceReviewDTO;
import com.emp.management.entity.EmployeeDetails;
import com.emp.management.entity.PerformanceReview;
import com.emp.management.entity.Role;
import com.emp.management.entity.User;
import com.emp.management.exception.BadRequestException;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.EmployeeDetailsRepository;
import com.emp.management.repository.PerformanceReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PerformanceServiceTest {

    @Mock private PerformanceReviewRepository reviewRepository;
    @Mock private EmployeeDetailsRepository employeeDetailsRepository;

    @InjectMocks private PerformanceService performanceService;

    private User adminUser;
    private EmployeeDetails adminEmployee;
    private User empUser;
    private EmployeeDetails employee;
    private PerformanceReview review;

    @BeforeEach
    void setUp() {
        adminUser = User.builder().id(1L).email("admin@company.com").role(Role.ADMIN).active(true).build();
        adminEmployee = EmployeeDetails.builder().id(1L).user(adminUser).firstName("Admin").lastName("User")
                .employeeCode("10001").active(true).build();

        empUser = User.builder().id(2L).email("emp@company.com").role(Role.EMPLOYEE).active(true).build();
        employee = EmployeeDetails.builder().id(2L).user(empUser).firstName("John").lastName("Doe")
                .employeeCode("10002").active(true).build();

        review = PerformanceReview.builder()
                .id(100L)
                .employee(employee)
                .reviewer(adminEmployee)
                .rating(4)
                .comments("Good work")
                .strengths("Communication")
                .areasOfImprovement("Time management")
                .reviewDate(LocalDate.of(2025, 1, 15))
                .reviewPeriod("Q1 2025")
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ── deleteReview — success ────────────────────────────────────────────────

    @Test
    void deleteReview_existingId_deletesReview() {
        when(reviewRepository.findById(100L)).thenReturn(Optional.of(review));
        doNothing().when(reviewRepository).delete(review);

        performanceService.deleteReview(100L);

        verify(reviewRepository).findById(100L);
        verify(reviewRepository).delete(review);
    }

    @Test
    void deleteReview_existingId_deletesExactReviewObject() {
        when(reviewRepository.findById(100L)).thenReturn(Optional.of(review));

        performanceService.deleteReview(100L);

        verify(reviewRepository).delete(review);
        verifyNoMoreInteractions(reviewRepository);
    }

    // ── deleteReview — not found ──────────────────────────────────────────────

    @Test
    void deleteReview_nonExistingId_throwsResourceNotFoundException() {
        when(reviewRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> performanceService.deleteReview(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    void deleteReview_nonExistingId_neverCallsDelete() {
        when(reviewRepository.findById(999L)).thenReturn(Optional.empty());

        try {
            performanceService.deleteReview(999L);
        } catch (ResourceNotFoundException ignored) {}

        verify(reviewRepository, never()).delete(any());
    }

    @Test
    void deleteReview_zeroId_throwsResourceNotFoundException() {
        when(reviewRepository.findById(0L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> performanceService.deleteReview(0L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteReview_negativeId_throwsResourceNotFoundException() {
        when(reviewRepository.findById(-1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> performanceService.deleteReview(-1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── deleteReview — error message ─────────────────────────────────────────

    @Test
    void deleteReview_notFound_errorMessageContainsResourceName() {
        when(reviewRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> performanceService.deleteReview(42L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("PerformanceReview");
    }

    // ── createReview — happy path and validation ──────────────────────────────

    @Test
    void createReview_validRating_returnsDTO() {
        PerformanceReviewDTO dto = PerformanceReviewDTO.builder()
                .employeeId(2L).reviewerId(1L).rating(4)
                .comments("Great").reviewDate(LocalDate.now()).reviewPeriod("Q2 2025").build();

        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(employee));
        when(employeeDetailsRepository.findById(1L)).thenReturn(Optional.of(adminEmployee));
        when(reviewRepository.save(any())).thenReturn(review);

        PerformanceReviewDTO result = performanceService.createReview(dto);

        assertThat(result).isNotNull();
        assertThat(result.getRating()).isEqualTo(4);
    }

    @Test
    void createReview_ratingBelow1_throwsBadRequestException() {
        PerformanceReviewDTO dto = PerformanceReviewDTO.builder()
                .employeeId(2L).reviewerId(1L).rating(0)
                .reviewDate(LocalDate.now()).reviewPeriod("Q2 2025").build();

        assertThatThrownBy(() -> performanceService.createReview(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Rating must be between 1 and 5");
    }

    @Test
    void createReview_ratingAbove5_throwsBadRequestException() {
        PerformanceReviewDTO dto = PerformanceReviewDTO.builder()
                .employeeId(2L).reviewerId(1L).rating(6)
                .reviewDate(LocalDate.now()).reviewPeriod("Q2 2025").build();

        assertThatThrownBy(() -> performanceService.createReview(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Rating must be between 1 and 5");
    }

    @Test
    void createReview_boundary_rating1_succeeds() {
        PerformanceReviewDTO dto = PerformanceReviewDTO.builder()
                .employeeId(2L).reviewerId(1L).rating(1)
                .reviewDate(LocalDate.now()).reviewPeriod("Q2 2025").build();

        PerformanceReview lowReview = PerformanceReview.builder()
                .id(101L).employee(employee).reviewer(adminEmployee).rating(1)
                .reviewDate(LocalDate.now()).reviewPeriod("Q2 2025").createdAt(LocalDateTime.now()).build();

        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(employee));
        when(employeeDetailsRepository.findById(1L)).thenReturn(Optional.of(adminEmployee));
        when(reviewRepository.save(any())).thenReturn(lowReview);

        PerformanceReviewDTO result = performanceService.createReview(dto);
        assertThat(result.getRating()).isEqualTo(1);
    }

    @Test
    void createReview_boundary_rating5_succeeds() {
        PerformanceReviewDTO dto = PerformanceReviewDTO.builder()
                .employeeId(2L).reviewerId(1L).rating(5)
                .reviewDate(LocalDate.now()).reviewPeriod("Q2 2025").build();

        PerformanceReview highReview = PerformanceReview.builder()
                .id(102L).employee(employee).reviewer(adminEmployee).rating(5)
                .reviewDate(LocalDate.now()).reviewPeriod("Q2 2025").createdAt(LocalDateTime.now()).build();

        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(employee));
        when(employeeDetailsRepository.findById(1L)).thenReturn(Optional.of(adminEmployee));
        when(reviewRepository.save(any())).thenReturn(highReview);

        PerformanceReviewDTO result = performanceService.createReview(dto);
        assertThat(result.getRating()).isEqualTo(5);
    }

    @Test
    void createReview_unknownEmployeeId_throwsResourceNotFoundException() {
        PerformanceReviewDTO dto = PerformanceReviewDTO.builder()
                .employeeId(999L).reviewerId(1L).rating(3)
                .reviewDate(LocalDate.now()).reviewPeriod("Q2 2025").build();

        when(employeeDetailsRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> performanceService.createReview(dto))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createReview_unknownReviewerId_throwsResourceNotFoundException() {
        PerformanceReviewDTO dto = PerformanceReviewDTO.builder()
                .employeeId(2L).reviewerId(999L).rating(3)
                .reviewDate(LocalDate.now()).reviewPeriod("Q2 2025").build();

        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(employee));
        when(employeeDetailsRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> performanceService.createReview(dto))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getEmployeeReviews ────────────────────────────────────────────────────

    @Test
    void getEmployeeReviews_existingEmployee_returnsList() {
        when(reviewRepository.findByEmployeeIdOrderByReviewDateDesc(2L)).thenReturn(List.of(review));

        List<PerformanceReviewDTO> result = performanceService.getEmployeeReviews(2L, "caller@company.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmployeeName()).isEqualTo("John Doe");
    }

    @Test
    void getEmployeeReviews_noReviews_returnsEmptyList() {
        when(reviewRepository.findByEmployeeIdOrderByReviewDateDesc(2L)).thenReturn(List.of());

        List<PerformanceReviewDTO> result = performanceService.getEmployeeReviews(2L, "caller@company.com");

        assertThat(result).isEmpty();
    }

    // ── getAllReviews ─────────────────────────────────────────────────────────

    @Test
    void getAllReviews_returnsActiveEmployeeReviews() {
        when(reviewRepository.findAllActiveEmployeeReviews()).thenReturn(List.of(review));

        List<PerformanceReviewDTO> result = performanceService.getAllReviews();

        assertThat(result).hasSize(1);
    }

    @Test
    void getAllReviews_empty_returnsEmptyList() {
        when(reviewRepository.findAllActiveEmployeeReviews()).thenReturn(List.of());

        List<PerformanceReviewDTO> result = performanceService.getAllReviews();

        assertThat(result).isEmpty();
    }

    // ── getReviewsByReviewer ──────────────────────────────────────────────────

    @Test
    void getReviewsByReviewer_existingReviewer_returnsList() {
        when(reviewRepository.findByReviewerIdOrderByReviewDateDesc(1L)).thenReturn(List.of(review));

        List<PerformanceReviewDTO> result = performanceService.getReviewsByReviewer(1L, "caller@company.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getReviewerName()).isEqualTo("Admin User");
    }

    // ── getAverageRating ──────────────────────────────────────────────────────

    @Test
    void getAverageRating_returnsRepositoryValue() {
        when(reviewRepository.getAverageRatingByEmployee(2L)).thenReturn(3.75);

        Double avg = performanceService.getAverageRating(2L, "caller@company.com");

        assertThat(avg).isEqualTo(3.75);
    }

    @Test
    void getAverageRating_noReviews_returnsNull() {
        when(reviewRepository.getAverageRatingByEmployee(99L)).thenReturn(null);

        Double avg = performanceService.getAverageRating(99L, "caller@company.com");

        assertThat(avg).isNull();
    }

    // ── DTO mapping correctness ───────────────────────────────────────────────

    @Test
    void createReview_dtoMapping_allFieldsCorrectlyMapped() {
        PerformanceReviewDTO dto = PerformanceReviewDTO.builder()
                .employeeId(2L).reviewerId(1L).rating(5)
                .comments("Excellent").strengths("Leadership").areasOfImprovement("Delegation")
                .reviewDate(LocalDate.of(2025, 3, 10)).reviewPeriod("Q1 2025").build();

        PerformanceReview saved = PerformanceReview.builder()
                .id(200L).employee(employee).reviewer(adminEmployee).rating(5)
                .comments("Excellent").strengths("Leadership").areasOfImprovement("Delegation")
                .reviewDate(LocalDate.of(2025, 3, 10)).reviewPeriod("Q1 2025")
                .createdAt(LocalDateTime.now()).build();

        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(employee));
        when(employeeDetailsRepository.findById(1L)).thenReturn(Optional.of(adminEmployee));
        when(reviewRepository.save(any())).thenReturn(saved);

        PerformanceReviewDTO result = performanceService.createReview(dto);

        assertThat(result.getId()).isEqualTo(200L);
        assertThat(result.getEmployeeId()).isEqualTo(2L);
        assertThat(result.getEmployeeName()).isEqualTo("John Doe");
        assertThat(result.getReviewerId()).isEqualTo(1L);
        assertThat(result.getReviewerName()).isEqualTo("Admin User");
        assertThat(result.getRating()).isEqualTo(5);
        assertThat(result.getComments()).isEqualTo("Excellent");
        assertThat(result.getStrengths()).isEqualTo("Leadership");
        assertThat(result.getAreasOfImprovement()).isEqualTo("Delegation");
        assertThat(result.getReviewPeriod()).isEqualTo("Q1 2025");
    }
}
