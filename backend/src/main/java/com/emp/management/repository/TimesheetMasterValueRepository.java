package com.emp.management.repository;

import com.emp.management.entity.MasterDataType;
import com.emp.management.entity.TimesheetMasterValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TimesheetMasterValueRepository extends JpaRepository<TimesheetMasterValue, Long> {

    List<TimesheetMasterValue> findByTypeOrderByValueAsc(MasterDataType type);

    List<TimesheetMasterValue> findByTypeAndActiveTrueOrderByValueAsc(MasterDataType type);

    List<TimesheetMasterValue> findByTypeOrderByPeriodEndDateAsc(MasterDataType type);

    List<TimesheetMasterValue> findByTypeAndActiveTrueOrderByPeriodEndDateAsc(MasterDataType type);

    boolean existsByTypeAndValueIgnoreCase(MasterDataType type, String value);

    Optional<TimesheetMasterValue> findByTypeAndValueIgnoreCase(MasterDataType type, String value);
}
