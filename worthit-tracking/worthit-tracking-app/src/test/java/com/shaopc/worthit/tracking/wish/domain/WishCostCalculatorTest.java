package com.shaopc.worthit.tracking.wish.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class WishCostCalculatorTest {

    @Test
    void matchesFrozenPlanDailyGoldCases() {
        WishCost regular = WishCostCalculator.calculate(
                new BigDecimal("1000"),
                new BigDecimal("1"),
                null);
        assertThat(regular.expectedUseDays()).isEqualTo(365);
        assertThat(regular.planDailyCostDisplay())
                .isEqualTo("¥2.74/天");
        assertThat(regular.residualUnset()).isTrue();

        WishCost floor = WishCostCalculator.calculate(
                new BigDecimal("100"),
                new BigDecimal("1"),
                new BigDecimal("200"));
        assertThat(floor.planDailyCostDisplay())
                .isEqualTo("¥0.00/天");

        WishCost tiny = WishCostCalculator.calculate(
                BigDecimal.ONE, BigDecimal.ONE, null);
        assertThat(tiny.planDailyCostDisplay())
                .isEqualTo("<¥0.01/天");
        assertThat(tiny.planDailyCostTiny()).isTrue();
    }

    @Test
    void roundsFractionalYearsUpToWholeDays() {
        WishCost cost = WishCostCalculator.calculate(
                new BigDecimal("183"),
                new BigDecimal("0.5"),
                null);

        assertThat(cost.expectedUseDays()).isEqualTo(183);
        assertThat(cost.planDailyCost())
                .isEqualByComparingTo("1.00");
    }

    @Test
    void distinguishesUnsetResidualFromExplicitZero() {
        WishCost unset = WishCostCalculator.calculate(
                new BigDecimal("365"),
                BigDecimal.ONE,
                null);
        WishCost zero = WishCostCalculator.calculate(
                new BigDecimal("365"),
                BigDecimal.ONE,
                BigDecimal.ZERO);

        assertThat(unset.residualUnset()).isTrue();
        assertThat(zero.residualUnset()).isFalse();
        assertThat(unset.planDailyCost())
                .isEqualByComparingTo(zero.planDailyCost());
    }
}
