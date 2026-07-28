package com.shaopc.worthit.tracking.subscription.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionCostCalculatorTest {

    @ParameterizedTest
    @MethodSource("costCases")
    void calculatesFrozenMonthlyCostCases(
            String amount,
            String currency,
            BillingCycleType cycleType,
            Integer cycleValue,
            String cnyReference,
            String originalDisplay,
            String cnyDisplay,
            boolean approximate,
            boolean include) {
        SubscriptionMonthlyCost result =
                SubscriptionCostCalculator.calculate(
                        new BigDecimal(amount),
                        currency,
                        cycleType,
                        cycleValue,
                        cnyReference == null
                                ? null
                                : new BigDecimal(cnyReference));

        assertThat(result.originalMonthlyCostDisplay())
                .isEqualTo(originalDisplay);
        assertThat(result.cnyMonthlyCostDisplay())
                .isEqualTo(cnyDisplay);
        assertThat(result.cnyApproximate())
                .isEqualTo(approximate);
        assertThat(result.includeInCnyTotal())
                .isEqualTo(include);
    }

    private static Stream<Arguments> costCases() {
        return Stream.of(
                Arguments.of(
                        "20",
                        "USD",
                        BillingCycleType.MONTHLY,
                        null,
                        null,
                        "20.00 USD/月",
                        null,
                        false,
                        false),
                Arguments.of(
                        "20",
                        "USD",
                        BillingCycleType.MONTHLY,
                        null,
                        "140",
                        "20.00 USD/月",
                        "约 ¥140.00/月",
                        true,
                        true),
                Arguments.of(
                        "120",
                        "CNY",
                        BillingCycleType.YEARLY,
                        null,
                        null,
                        "¥10.00/月",
                        "¥10.00/月",
                        false,
                        true),
                Arguments.of(
                        "90",
                        "CNY",
                        BillingCycleType.MULTI_MONTH,
                        3,
                        null,
                        "¥30.00/月",
                        "¥30.00/月",
                        false,
                        true),
                Arguments.of(
                        "365",
                        "CNY",
                        BillingCycleType.FIXED_DAYS,
                        365,
                        null,
                        "¥30.42/月",
                        "¥30.42/月",
                        false,
                        true));
    }
}
