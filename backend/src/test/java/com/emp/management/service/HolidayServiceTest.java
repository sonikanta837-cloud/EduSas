package com.emp.management.service;

import com.emp.management.dto.HolidayDTO;
import com.emp.management.entity.Holiday;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.HolidayRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
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

    // ── countDeductibleDays — weekends and holidays excluded ─────────────────

    @Test
    void countDeductibleDays_excludesWeekendsAndHolidays() {
        // Mon Jan 5 2026 .. Sun Jan 11 2026 = 5 weekdays; one of them (Wed Jan 7) is a holiday
        LocalDate start = LocalDate.of(2026, 1, 5);
        LocalDate end = LocalDate.of(2026, 1, 11);
        Holiday holiday = Holiday.builder().id(1L).name("Republic Day Observance")
                .date(LocalDate.of(2026, 1, 7)).location("ALL").build();
        when(holidayRepository.findApplicable(eq("Pune"), isNull(), eq(start), eq(end)))
                .thenReturn(List.of(holiday));

        int result = holidayService.countDeductibleDays(start, end, "Pune", null);

        assertThat(result).isEqualTo(4); // 5 weekdays - 1 holiday
    }

    @Test
    void countDeductibleDays_noHolidays_countsAllWeekdaysOnly() {
        LocalDate start = LocalDate.of(2026, 1, 5); // Monday
        LocalDate end = LocalDate.of(2026, 1, 11); // Sunday
        when(holidayRepository.findApplicable(anyString(), any(), any(), any())).thenReturn(List.of());

        int result = holidayService.countDeductibleDays(start, end, "Pune", null);

        assertThat(result).isEqualTo(5);
    }

    @Test
    void countDeductibleDays_singleWeekendDay_countsZero() {
        LocalDate saturday = LocalDate.of(2026, 1, 3);
        when(holidayRepository.findApplicable(anyString(), any(), any(), any())).thenReturn(List.of());

        int result = holidayService.countDeductibleDays(saturday, saturday, "Pune", null);

        assertThat(result).isEqualTo(0);
    }

    // ── getDistinctYears ───────────────────────────────────────────────────────

    @Test
    void getDistinctYears_addsCurrentYearIfMissing() {
        when(holidayRepository.findDistinctYears()).thenReturn(new ArrayList<>(List.of(2024, 2023)));

        List<Integer> result = holidayService.getDistinctYears();

        assertThat(result).contains(LocalDate.now().getYear());
    }

    @Test
    void getDistinctYears_currentYearAlreadyPresent_notDuplicated() {
        int current = LocalDate.now().getYear();
        when(holidayRepository.findDistinctYears()).thenReturn(new ArrayList<>(List.of(current, current - 1)));

        List<Integer> result = holidayService.getDistinctYears();

        assertThat(result).containsOnlyOnce(current);
    }

    // ── create / update — location & department blank-handling ──────────────

    @Test
    void create_blankLocation_normalizedToEmptyString() {
        HolidayDTO dto = HolidayDTO.builder().name("Diwali").date(LocalDate.now())
                .holidayType("Festival").location("   ").build();
        ArgumentCaptor<Holiday> captor = ArgumentCaptor.forClass(Holiday.class);
        when(holidayRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        holidayService.create(dto);

        assertThat(captor.getValue().getLocation()).isEmpty();
    }

    @Test
    void create_blankDepartment_normalizedToNull() {
        HolidayDTO dto = HolidayDTO.builder().name("Diwali").date(LocalDate.now())
                .holidayType("Festival").department("").build();
        ArgumentCaptor<Holiday> captor = ArgumentCaptor.forClass(Holiday.class);
        when(holidayRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        holidayService.create(dto);

        assertThat(captor.getValue().getDepartment()).isNull();
    }

    @Test
    void update_notFound_throwsResourceNotFoundException() {
        when(holidayRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> holidayService.update(999L, HolidayDTO.builder().name("x").build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_existing_updatesAllFields() {
        Holiday existing = Holiday.builder().id(1L).name("Old").date(LocalDate.now()).holidayType("Festival").build();
        HolidayDTO dto = HolidayDTO.builder().name("New Name").date(LocalDate.now().plusDays(1))
                .holidayType("National").active(true).build();
        when(holidayRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(holidayRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HolidayDTO result = holidayService.update(1L, dto);

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getHolidayType()).isEqualTo("National");
    }

    @Test
    void delete_notFound_throwsResourceNotFoundException() {
        when(holidayRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> holidayService.delete(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── bulkCreate ─────────────────────────────────────────────────────────────

    @Test
    void bulkCreate_savesEachEntryIndividually() {
        HolidayDTO d1 = HolidayDTO.builder().name("H1").date(LocalDate.now()).holidayType("Festival").build();
        HolidayDTO d2 = HolidayDTO.builder().name("H2").date(LocalDate.now().plusDays(1)).holidayType("National").build();
        when(holidayRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<HolidayDTO> result = holidayService.bulkCreate(List.of(d1, d2));

        assertThat(result).hasSize(2);
        verify(holidayRepository, times(2)).save(any());
    }

    // ── getApplicableHolidayDates — used by LeaveService ──────────────────────

    @Test
    void getApplicableHolidayDates_blankLocation_defaultsToALL() {
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusDays(5);
        when(holidayRepository.findApplicable(eq("ALL"), isNull(), eq(start), eq(end))).thenReturn(List.of());

        Set<LocalDate> result = holidayService.getApplicableHolidayDates(start, end, "", null);

        assertThat(result).isEmpty();
        verify(holidayRepository).findApplicable(eq("ALL"), isNull(), eq(start), eq(end));
    }

    @Test
    void getAll_withYear_delegatesToFindByYear() {
        when(holidayRepository.findByYear(2026)).thenReturn(List.of(
                Holiday.builder().id(1L).name("H").date(LocalDate.now()).build()));

        List<HolidayDTO> result = holidayService.getAll(2026);

        assertThat(result).hasSize(1);
        verify(holidayRepository, never()).findAllOrderByDate();
    }

    @Test
    void getAll_noYear_delegatesToFindAllOrderByDate() {
        when(holidayRepository.findAllOrderByDate()).thenReturn(List.of());

        holidayService.getAll(null);

        verify(holidayRepository).findAllOrderByDate();
        verify(holidayRepository, never()).findByYear(anyInt());
    }
}
