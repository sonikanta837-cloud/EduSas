package com.emp.management.service;

import com.emp.management.dto.MasterValueCreateRequest;
import com.emp.management.dto.MasterValueUpdateRequest;
import com.emp.management.dto.TimesheetMasterValueDTO;
import com.emp.management.entity.MasterDataType;
import com.emp.management.entity.TimesheetMasterValue;
import com.emp.management.exception.BadRequestException;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.TimesheetMasterValueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimesheetMasterValueService {

    private static final DateTimeFormatter PERIOD_END_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final TimesheetMasterValueRepository repository;

    @Transactional(readOnly = true)
    public List<TimesheetMasterValueDTO> list(MasterDataType type, boolean activeOnly) {
        List<TimesheetMasterValue> values = type == MasterDataType.PERIOD_END
                ? (activeOnly ? repository.findByTypeAndActiveTrueOrderByPeriodEndDateAsc(type)
                              : repository.findByTypeOrderByPeriodEndDateAsc(type))
                : (activeOnly ? repository.findByTypeAndActiveTrueOrderByValueAsc(type)
                              : repository.findByTypeOrderByValueAsc(type));
        return values.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional
    public TimesheetMasterValueDTO add(MasterDataType type, MasterValueCreateRequest req, String requesterName) {
        String value;
        if (type == MasterDataType.PERIOD_END) {
            if (req.getPeriodEndDate() == null) {
                throw new BadRequestException("Period end date is required");
            }
            value = req.getPeriodEndDate().format(PERIOD_END_FORMAT);
        } else {
            if (req.getValue() == null || req.getValue().isBlank()) {
                throw new BadRequestException("Value is required");
            }
            value = req.getValue().trim();
        }

        // Idempotent add: if an active value with this name already exists, return it
        // rather than erroring — avoids punishing a user for re-selecting the same "new" entry.
        var existing = repository.findByTypeAndValueIgnoreCase(type, value);
        if (existing.isPresent()) {
            return toDTO(existing.get());
        }

        try {
            TimesheetMasterValue saved = repository.save(TimesheetMasterValue.builder()
                    .type(type)
                    .value(value)
                    .periodEndDate(type == MasterDataType.PERIOD_END ? req.getPeriodEndDate() : null)
                    .active(true)
                    .createdByName(requesterName)
                    .build());
            return toDTO(saved);
        } catch (DataIntegrityViolationException e) {
            // Concurrent add of the same value — fetch and return the row the other request created.
            return repository.findByTypeAndValueIgnoreCase(type, value)
                    .map(this::toDTO)
                    .orElseThrow(() -> e);
        }
    }

    @Transactional
    public TimesheetMasterValueDTO update(Long id, MasterValueUpdateRequest req) {
        TimesheetMasterValue existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Master value", id));

        if (existing.getType() == MasterDataType.PERIOD_END) {
            if (req.getPeriodEndDate() != null) {
                existing.setPeriodEndDate(req.getPeriodEndDate());
                existing.setValue(req.getPeriodEndDate().format(PERIOD_END_FORMAT));
            }
        } else if (req.getValue() != null && !req.getValue().isBlank()) {
            String value = req.getValue().trim();
            repository.findByTypeAndValueIgnoreCase(existing.getType(), value).ifPresent(dup -> {
                if (!dup.getId().equals(id)) {
                    throw new BadRequestException("A value with this name already exists");
                }
            });
            existing.setValue(value);
        }

        existing.setActive(req.isActive());
        return toDTO(repository.save(existing));
    }

    private TimesheetMasterValueDTO toDTO(TimesheetMasterValue v) {
        return TimesheetMasterValueDTO.builder()
                .id(v.getId())
                .type(v.getType().name())
                .value(v.getValue())
                .periodEndDate(v.getPeriodEndDate())
                .active(v.isActive())
                .createdByName(v.getCreatedByName())
                .createdAt(v.getCreatedAt())
                .updatedAt(v.getUpdatedAt())
                .build();
    }
}
