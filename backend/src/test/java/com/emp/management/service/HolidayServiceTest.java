package com.emp.management.service;

import com.emp.management.dto.HolidayDTO;
import com.emp.management.entity.Holiday;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.HolidayRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HolidayServiceTest {

    @Mock private HolidayRepository holidayRepository;

    @InjectMocks private HolidayService holidayService;

    private Holiday publicHoliday;
    private Holiday locationHoliday;

    @BeforeEach
    void setUp() {
        publicHoliday = Holiday.builder()
                .id(1L)
                .name("Republic Day")
                .date(LocalDate.of(2026, 1, 26))
                .holidayType("PUBLIC")
                .location("")
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        locationHoliday = Holiday.builder()
                .id(2L)
                .name("Diwali")
                .date(LocalDate.of(2026, 11, 1))
                .holidayType("FESTIVAL")
                .location("Mumbai")
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ── getAll ────────────────────────────────────────────────────────────────

    @Test
    void getAll_noYearFilter_returnsAllHolidays() {
        when(holidayRepository.findAllOrderByDate()).thenReturn(List.of(publicHoliday, locationHoliday));

        List<HolidayDTO> result = holidayService.getAll(null);

        assertThat(result).hasSize(2);
    }

    @Test
    void getAll_withYear_returnsFilteredHolidays() {
        when(holidayRepository.findByYear(2026)).thenReturn(List.of(publicHoliday));

        List<HolidayDTO> result = holidayService.getAll(2026);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Republic Day");
    }

    @Test
    void getAll_emptyRepository_returnsEmptyList() {
        when(holidayRepository.findAllOrderByDate()).thenReturn(List.of());

        List<HolidayDTO> result = holidayService.getAll(null);

        assertThat(result).isEmpty();
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_validHoliday_returnsSavedDTO() {
        HolidayDTO dto = HolidayDTO.builder()
                .name("New Holiday").date(LocalDate.of(2026, 5, 1))
                .holidayType("PUBLIC").location("ALL").active(true).build();

        when(holidayRepository.save(any())).thenReturn(publicHoliday);

        HolidayDTO result = holidayService.create(dto);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Republic Day");
        verify(holidayRepository).save(any(Holiday.class));
    }

    @Test
    void create_nullLocation_savesWithEmptyString() {
        HolidayDTO dto = HolidayDTO.builder()
                .name("Test").date(LocalDate.now().plusDays(30))
                .holidayType("PUBLIC").location(null).active(true).build();

        when(holidayRepository.save(any())).thenAnswer(inv -> {
            Holiday h = inv.getArgument(0);
            assertThat(h.getLocation()).isEqualTo("");
            h.setId(99L);
            h.setCreatedAt(LocalDateTime.now());
            h.setUpdatedAt(LocalDateTime.now());
            return h;
        });

        HolidayDTO result = holidayService.create(dto);
        assertThat(result).isNotNull();
    }

    @Test
    void create_blankDepartment_savesWithNull() {
        HolidayDTO dto = HolidayDTO.builder()
                .name("Test").date(LocalDate.now().plusDays(30))
                .holidayType("PUBLIC").department("").active(true).build();

        when(holidayRepository.save(any())).thenAnswer(inv -> {
            Holiday h = inv.getArgument(0);
            assertThat(h.getDepartment()).isNull();
            h.setId(99L);
            h.setCreatedAt(LocalDateTime.now());
            h.setUpdatedAt(LocalDateTime.now());
            return h;
        });

        holidayService.create(dto);
        verify(holidayRepository).save(any());
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void update_existingHoliday_returnsUpdatedDTO() {
        HolidayDTO dto = HolidayDTO.builder()
                .name("Updated Republic Day").date(LocalDate.of(2026, 1, 26))
                .holidayType("PUBLIC").active(true).build();

        when(holidayRepository.findById(1L)).thenReturn(Optional.of(publicHoliday));
        when(holidayRepository.save(any())).thenReturn(publicHoliday);

        HolidayDTO result = holidayService.update(1L, dto);

        assertThat(result).isNotNull();
        verify(holidayRepository).save(publicHoliday);
    }

    @Test
    void update_nonExistingHoliday_throwsResourceNotFoundException() {
        when(holidayRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> holidayService.update(999L,
                HolidayDTO.builder().name("X").date(LocalDate.now()).holidayType("PUBLIC").build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_existingHoliday_deletesSuccessfully() {
        when(holidayRepository.findById(1L)).thenReturn(Optional.of(publicHoliday));
        doNothing().when(holidayRepository).delete(publicHoliday);

        holidayService.delete(1L);

        verify(holidayRepository).delete(publicHoliday);
    }

    @Test
    void delete_nonExistingHoliday_throwsResourceNotFoundException() {
        when(holidayRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> holidayService.delete(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── bulkCreate ────────────────────────────────────────────────────────────

    @Test
    void bulkCreate_multipleHolidays_savesAll() {
        HolidayDTO dto1 = HolidayDTO.builder().name("H1").date(LocalDate.of(2026, 1, 1))
                .holidayType("PUBLIC").active(true).build();
        HolidayDTO dto2 = HolidayDTO.builder().name("H2").date(LocalDate.of(2026, 8, 15))
                .holidayType("PUBLIC").active(true).build();

        when(holidayRepository.save(any()))
                .thenReturn(publicHoliday)
                .thenReturn(locationHoliday);

        List<HolidayDTO> result = holidayService.bulkCreate(List.of(dto1, dto2));

        assertThat(result).hasSize(2);
        verify(holidayRepository, times(2)).save(any());
    }

    // ── countDeductibleDays ───────────────────────────────────────────────────

    @Test
    void countDeductibleDays_mondayToFriday_returns5Days() {
        // Find a Monday
        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY).plusWeeks(2);
        LocalDate friday = monday.plusDays(4);

        when(holidayRepository.findApplicable(anyString(), any(), any(), any())).thenReturn(List.of());

        int days = holidayService.countDeductibleDays(monday, friday, "ALL", null);

        assertThat(days).isEqualTo(5);
    }

    @Test
    void countDeductibleDays_weekendOnly_returns0Days() {
        LocalDate saturday = LocalDate.now().with(DayOfWeek.SATURDAY).plusWeeks(2);
        LocalDate sunday = saturday.plusDays(1);

        when(holidayRepository.findApplicable(anyString(), any(), any(), any())).thenReturn(List.of());

        int days = holidayService.countDeductibleDays(saturday, sunday, "ALL", null);

        assertThat(days).isEqualTo(0);
    }

    @Test
    void countDeductibleDays_withHoliday_excludesHoliday() {
        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY).plusWeeks(2);
        LocalDate friday = monday.plusDays(4);
        LocalDate wednesday = monday.plusDays(2);

        Holiday midweekHoliday = Holiday.builder()
                .id(5L).name("Test Holiday").date(wednesday)
                .holidayType("PUBLIC").location("").active(true)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();

        when(holidayRepository.findApplicable(anyString(), any(), any(), any()))
                .thenReturn(List.of(midweekHoliday));

        int days = holidayService.countDeductibleDays(monday, friday, "ALL", null);

        assertThat(days).isEqualTo(4); // 5 working days minus 1 holiday
    }

    @Test
    void countDeductibleDays_singleDay_returns1() {
        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY).plusWeeks(2);
        when(holidayRepository.findApplicable(anyString(), any(), any(), any())).thenReturn(List.of());

        int days = holidayService.countDeductibleDays(monday, monday, "ALL", null);

        assertThat(days).isEqualTo(1);
    }

    // ── getApplicableHolidayDates ─────────────────────────────────────────────

    @Test
    void getApplicableHolidayDates_returnsHolidayDateSet() {
        LocalDate start = LocalDate.of(2026, 1, 20);
        LocalDate end = LocalDate.of(2026, 2, 5);

        when(holidayRepository.findApplicable(anyString(), any(), any(), any()))
                .thenReturn(List.of(publicHoliday));

        Set<LocalDate> dates = holidayService.getApplicableHolidayDates(start, end, "ALL", null);

        assertThat(dates).contains(LocalDate.of(2026, 1, 26));
    }

    @Test
    void getApplicableHolidayDates_noHolidays_returnsEmptySet() {
        LocalDate start = LocalDate.of(2026, 2, 1);
        LocalDate end = LocalDate.of(2026, 2, 28);

        when(holidayRepository.findApplicable(anyString(), any(), any(), any())).thenReturn(List.of());

        Set<LocalDate> dates = holidayService.getApplicableHolidayDates(start, end, "ALL", null);

        assertThat(dates).isEmpty();
    }

    // ── getDistinctYears ──────────────────────────────────────────────────────

    @Test
    void getDistinctYears_includesCurrentYearIfMissing() {
        int currentYear = LocalDate.now().getYear();
        // Use a mutable list so getDistinctYears() can call add(0, current)
        java.util.List<Integer> mutableList = new java.util.ArrayList<>(java.util.Arrays.asList(2024, 2025));
        when(holidayRepository.findDistinctYears()).thenReturn(mutableList);

        List<Integer> years = holidayService.getDistinctYears();

        assertThat(years).contains(currentYear);
    }

    @Test
    void getDistinctYears_currentYearAlreadyPresent_noDuplicate() {
        int currentYear = LocalDate.now().getYear();
        // Use mutable list
        java.util.List<Integer> mutableList = new java.util.ArrayList<>(java.util.Arrays.asList(currentYear, currentYear - 1));
        when(holidayRepository.findDistinctYears()).thenReturn(mutableList);

        List<Integer> years = holidayService.getDistinctYears();

        assertThat(years.stream().filter(y -> y == currentYear).count()).isEqualTo(1);
    }

    // ── DTO mapping ───────────────────────────────────────────────────────────

    @Test
    void toDTO_mapsAllFields() {
        when(holidayRepository.findAllOrderByDate()).thenReturn(List.of(publicHoliday));

        List<HolidayDTO> result = holidayService.getAll(null);

        HolidayDTO dto = result.get(0);
        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getName()).isEqualTo("Republic Day");
        assertThat(dto.getDate()).isEqualTo(LocalDate.of(2026, 1, 26));
        assertThat(dto.getHolidayType()).isEqualTo("PUBLIC");
        assertThat(dto.isActive()).isTrue();
    }
}
