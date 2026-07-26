package com.shaopc.worthit.tracking.item.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

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
}
