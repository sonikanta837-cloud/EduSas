package com.emp.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AssetEntityTest {

    // ── @Column(name = "asset_condition") annotation present ─────────────────

    @Test
    void conditionField_hasColumnAnnotation_withAssetConditionName() throws NoSuchFieldException {
        Field conditionField = Asset.class.getDeclaredField("condition");
        Column column = conditionField.getAnnotation(Column.class);

        assertThat(column).isNotNull();
        assertThat(column.name()).isEqualTo("asset_condition");
    }

    @Test
    void conditionField_isEnumeratedAsString() throws NoSuchFieldException {
        Field conditionField = Asset.class.getDeclaredField("condition");
        Enumerated enumerated = conditionField.getAnnotation(Enumerated.class);

        assertThat(enumerated).isNotNull();
        assertThat(enumerated.value()).isEqualTo(jakarta.persistence.EnumType.STRING);
    }

    @Test
    void conditionField_typeIsAssetCondition() throws NoSuchFieldException {
        Field conditionField = Asset.class.getDeclaredField("condition");
        assertThat(conditionField.getType()).isEqualTo(AssetCondition.class);
    }

    // ── Default value for condition ───────────────────────────────────────────

    @Test
    void asset_defaultCondition_isGood() {
        Asset asset = Asset.builder()
                .assetCode("AST-001")
                .assetName("Laptop")
                .assetType(AssetType.LAPTOP)
                .build();

        assertThat(asset.getCondition()).isEqualTo(AssetCondition.GOOD);
    }

    @Test
    void asset_defaultStatus_isAvailable() {
        Asset asset = Asset.builder()
                .assetCode("AST-001")
                .assetName("Laptop")
                .assetType(AssetType.LAPTOP)
                .build();

        assertThat(asset.getStatus()).isEqualTo(AssetStatus.AVAILABLE);
    }

    // ── Builder can set condition ─────────────────────────────────────────────

    @Test
    void asset_builderSetsCondition_explicitly() {
        Asset asset = Asset.builder()
                .assetCode("AST-002")
                .assetName("Phone")
                .assetType(AssetType.PHONE)
                .condition(AssetCondition.POOR)
                .build();

        assertThat(asset.getCondition()).isEqualTo(AssetCondition.POOR);
    }

    @Test
    void asset_builderSetsAllConditionValues() {
        for (AssetCondition cond : AssetCondition.values()) {
            Asset asset = Asset.builder()
                    .assetCode("AST-003")
                    .assetName("Monitor")
                    .assetType(AssetType.MONITOR)
                    .condition(cond)
                    .build();
            assertThat(asset.getCondition()).isEqualTo(cond);
        }
    }

    // ── Other fields not affected by rename ───────────────────────────────────

    @Test
    void asset_otherFieldsSetAndGet_normally() {
        Asset asset = Asset.builder()
                .id(1L)
                .assetCode("AST-001")
                .assetName("Laptop Pro")
                .assetType(AssetType.LAPTOP)
                .brand("Dell")
                .model("XPS 15")
                .serialNumber("SN12345")
                .purchaseDate(LocalDate.of(2024, 1, 15))
                .purchasePrice(new BigDecimal("120000.00"))
                .status(AssetStatus.ASSIGNED)
                .condition(AssetCondition.GOOD)
                .build();

        assertThat(asset.getId()).isEqualTo(1L);
        assertThat(asset.getAssetCode()).isEqualTo("AST-001");
        assertThat(asset.getAssetName()).isEqualTo("Laptop Pro");
        assertThat(asset.getBrand()).isEqualTo("Dell");
        assertThat(asset.getModel()).isEqualTo("XPS 15");
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.ASSIGNED);
        assertThat(asset.getCondition()).isEqualTo(AssetCondition.GOOD);
    }

    // ── No-arg constructor works ──────────────────────────────────────────────

    @Test
    void asset_noArgConstructor_createsInstance() {
        assertThatCode(() -> {
            Asset asset = new Asset();
            // Condition will be null without builder @Builder.Default
            // but the instance is created without exception
            assertThat(asset).isNotNull();
        }).doesNotThrowAnyException();
    }

    // ── Setter/Getter for condition ───────────────────────────────────────────

    @Test
    void asset_setCondition_getCondition_roundTrip() {
        Asset asset = new Asset();
        asset.setCondition(AssetCondition.FAIR);

        assertThat(asset.getCondition()).isEqualTo(AssetCondition.FAIR);
    }

    // ── Column name uniqueness — assetCode is unique+required ────────────────

    @Test
    void assetCodeField_hasUniqueAndNotNullConstraint() throws NoSuchFieldException {
        Field assetCodeField = Asset.class.getDeclaredField("assetCode");
        Column column = assetCodeField.getAnnotation(Column.class);

        assertThat(column).isNotNull();
        assertThat(column.unique()).isTrue();
        assertThat(column.nullable()).isFalse();
    }
}
