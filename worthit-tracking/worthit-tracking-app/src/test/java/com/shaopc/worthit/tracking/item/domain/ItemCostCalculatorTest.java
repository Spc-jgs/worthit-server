package com.shaopc.worthit.tracking.item.domain;

import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.tracking.lifecycle.domain.DisposalType;
import com.shaopc.worthit.tracking.lifecycle.domain.ItemDisposal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemCostCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 26);

    @Test
    void calculatesPlanCostAndKeepsNullResidualSemantics() {
        ItemCost cost = ItemCostCalculator.calculate(
                new BigDecimal("1000"),
                new BigDecimal("1"),
                null,
                null,
                TODAY);

        assertThat(cost.expectedUseDays()).isEqualTo(365);
        assertThat(cost.planDailyCost()).isEqualByComparingTo("2.74");
        assertThat(cost.planDailyCostDisplay()).isEqualTo("¥2.74/天");
        assertThat(cost.planDailyCostTiny()).isFalse();
        assertThat(cost.residualUnset()).isTrue();
        assertThat(cost.holdingDays()).isNull();
        assertThat(cost.holdingDailyCost()).isNull();
    }

    @Test
    void distinguishesExplicitZeroResidualAndRoundsHalfUp() {
        ItemCost cost = ItemCostCalculator.calculate(
                new BigDecimal("1000"),
                new BigDecimal("1"),
                BigDecimal.ZERO,
                null,
                TODAY);

        assertThat(cost.planDailyCost()).isEqualByComparingTo("2.74");
        assertThat(cost.residualUnset()).isFalse();
    }

    @Test
    void floorsPlanCostAtZeroWhenResidualExceedsPrice() {
        ItemCost cost = ItemCostCalculator.calculate(
                new BigDecimal("100"),
                new BigDecimal("1"),
                new BigDecimal("200"),
                null,
                TODAY);

        assertThat(cost.planDailyCost()).isEqualByComparingTo("0.00");
        assertThat(cost.planDailyCostDisplay()).isEqualTo("¥0.00/天");
    }

    @Test
    void usesCeilingForFractionalExpectedYears() {
        ItemCost cost = ItemCostCalculator.calculate(
                new BigDecimal("183"),
                new BigDecimal("0.5"),
                null,
                null,
                TODAY);

        assertThat(cost.expectedUseDays()).isEqualTo(183);
        assertThat(cost.planDailyCost()).isEqualByComparingTo("1.00");
    }

    @Test
    void marksPositiveSubCentDailyCostAsTiny() {
        ItemCost cost = ItemCostCalculator.calculate(
                BigDecimal.ONE,
                BigDecimal.ONE,
                null,
                null,
                TODAY);

        assertThat(cost.planDailyCost()).isEqualByComparingTo("0.00");
        assertThat(cost.planDailyCostDisplay()).isEqualTo("<¥0.01/天");
        assertThat(cost.planDailyCostTiny()).isTrue();
        assertThat(cost.exactPlanDailyCost())
                .isPositive()
                .isLessThan(new BigDecimal("0.01"));
    }

    @Test
    void exposesExactPlanDailyCostForAggregateBeforeRounding() {
        BigDecimal exact =
                ItemCostCalculator.calculateExactPlanDailyCost(
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        null);

        assertThat(exact)
                .isEqualByComparingTo(
                        BigDecimal.ONE.divide(
                                BigDecimal.valueOf(365),
                                java.math.MathContext.DECIMAL128));
    }

    @Test
    void calculatesInclusiveHoldingDays() {
        ItemCost cost = ItemCostCalculator.calculate(
                new BigDecimal("100"),
                BigDecimal.ONE,
                null,
                TODAY.minusDays(9),
                TODAY);

        assertThat(cost.holdingDays()).isEqualTo(10);
        assertThat(cost.holdingDailyCost()).isEqualByComparingTo("10.00");
        assertThat(cost.holdingDailyCostDisplay()).isEqualTo("¥10.00/天");
    }

    @Test
    void holdingItemUsesTodayAsInclusiveCostCutoff() {
        ItemCost cost = ItemCostCalculator.calculate(
                item(ItemLifecycleStatus.HOLDING),
                TODAY,
                null);

        assertThat(cost.holdingDays()).isEqualTo(10);
        assertThat(cost.holdingDailyCost())
                .isEqualByComparingTo("10.00");
    }

    @Test
    void terminalItemsUseMatchingDisposalDateAsCostCutoff() {
        for (DisposalType type : DisposalType.values()) {
            ItemCost cost = ItemCostCalculator.calculate(
                    item(type.targetStatus()),
                    TODAY.plusDays(30),
                    disposal(type, TODAY.minusDays(4)));

            assertThat(cost.holdingDays()).isEqualTo(6);
            assertThat(cost.holdingDailyCost())
                    .isEqualByComparingTo("16.67");
        }
    }

    @Test
    void terminalItemDisposedOnPurchaseDateHasOneHoldingDay() {
        ItemCost cost = ItemCostCalculator.calculate(
                item(ItemLifecycleStatus.RETURNED),
                TODAY.plusDays(30),
                disposal(
                        DisposalType.RETURNED,
                        TODAY.minusDays(9)));

        assertThat(cost.holdingDays()).isEqualTo(1);
        assertThat(cost.holdingDailyCost())
                .isEqualByComparingTo("100.00");
    }

    @Test
    void rejectsMissingOrMismatchedTerminalDisposalFact() {
        assertThatThrownBy(() -> ItemCostCalculator.calculate(
                item(ItemLifecycleStatus.SOLD),
                TODAY,
                null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("VAL_STATE_CONFLICT"));

        assertThatThrownBy(() -> ItemCostCalculator.calculate(
                item(ItemLifecycleStatus.SOLD),
                TODAY,
                disposal(DisposalType.RETURNED, TODAY)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("VAL_STATE_CONFLICT"));
    }

    @Test
    void rejectsDisposalFactForHoldingItem() {
        assertThatThrownBy(() -> ItemCostCalculator.calculate(
                item(ItemLifecycleStatus.HOLDING),
                TODAY,
                disposal(DisposalType.RETURNED, TODAY)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("VAL_STATE_CONFLICT"));
    }

    private static Item item(ItemLifecycleStatus status) {
        LocalDateTime time = TODAY.atStartOfDay();
        return new Item(
                1L,
                1001L,
                10L,
                "测试物品",
                new BigDecimal("100"),
                BigDecimal.ONE,
                null,
                TODAY.minusDays(9),
                null,
                false,
                null,
                null,
                status,
                1L,
                time,
                time);
    }

    private static ItemDisposal disposal(
            DisposalType type, LocalDate disposalDate) {
        LocalDateTime time = disposalDate.atStartOfDay();
        return new ItemDisposal(
                2L,
                1001L,
                1L,
                type,
                disposalDate,
                new BigDecimal("100"),
                type == DisposalType.SOLD
                        ? new BigDecimal("80")
                        : null,
                null,
                time,
                time);
    }
}
