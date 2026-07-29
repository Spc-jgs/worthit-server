package com.shaopc.worthit.tracking.dashboard.application;

import com.shaopc.worthit.common.security.context.UserContext;
import com.shaopc.worthit.tracking.security.CurrentUserProvider;
import com.shaopc.worthit.tracking.subscription.domain.BillingCycleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    private static final long USER_ID = 1001L;

    private final DashboardFactsQuery factsQuery =
            mock(DashboardFactsQuery.class);
    private final CurrentUserProvider currentUserProvider =
            () -> new UserContext(USER_ID);
    private final DashboardService service =
            new DashboardServiceImpl(
                    currentUserProvider, factsQuery);

    @BeforeEach
    void returnEmptyFactsByDefault() {
        when(factsQuery.findHoldingItems(USER_ID))
                .thenReturn(List.of());
        when(factsQuery.findActiveSubscriptions(USER_ID))
                .thenReturn(List.of());
        when(factsQuery.aggregateConsideringWishes(USER_ID))
                .thenReturn(new DashboardWishAggregate(
                        0, BigDecimal.ZERO));
    }

    @Test
    void returnsZeroContractForEmptyAccount() {
        DashboardResult result = service.summary();

        assertThat(result.itemPlanDailyTotal())
                .isEqualTo("0.00");
        assertThat(result.itemPlanDailyTotalDisplay())
                .isEqualTo("¥0.00/天");
        assertThat(result.itemResidualUnsetCount()).isZero();
        assertThat(result.subscriptionMonthlyCnyTotal())
                .isEqualTo("0.00");
        assertThat(result.subscriptionMonthlyCnyTotalDisplay())
                .isEqualTo("¥0.00/月");
        assertThat(result.subscriptionMonthlyCnyApproximate())
                .isFalse();
        assertThat(result.subscriptionUnconvertedForeignCount())
                .isZero();
        assertThat(result.wishConsideringCount()).isZero();
        assertThat(result.wishConsideringAmountTotal())
                .isEqualTo("0.00");
        assertThat(result.wishConsideringAmountTotalDisplay())
                .isEqualTo("¥0.00");

        verify(factsQuery).findHoldingItems(USER_ID);
        verify(factsQuery).findActiveSubscriptions(USER_ID);
        verify(factsQuery).aggregateConsideringWishes(USER_ID);
    }

    @Test
    void sumsExactItemCostsBeforeFinalRounding() {
        when(factsQuery.findHoldingItems(USER_ID))
                .thenReturn(List.of(
                        itemFact("1", "1", null),
                        itemFact("1", "1", null)));

        DashboardResult result = service.summary();

        assertThat(result.itemPlanDailyTotal())
                .isEqualTo("0.01");
        assertThat(result.itemPlanDailyTotalDisplay())
                .isEqualTo("¥0.01/天");
        assertThat(result.itemResidualUnsetCount())
                .isEqualTo(2);
    }

    @Test
    void matchesHomeGoldAndKeepsForeignSemantics() {
        when(factsQuery.findHoldingItems(USER_ID))
                .thenReturn(List.of(
                        itemFact("1000", "1", null)));
        when(factsQuery.findActiveSubscriptions(USER_ID))
                .thenReturn(List.of(
                        subscriptionFact(
                                "20",
                                "USD",
                                BillingCycleType.MONTHLY,
                                null,
                                "140"),
                        subscriptionFact(
                                "120",
                                "CNY",
                                BillingCycleType.YEARLY,
                                null,
                                null),
                        subscriptionFact(
                                "30",
                                "EUR",
                                BillingCycleType.MONTHLY,
                                null,
                                null)));
        when(factsQuery.aggregateConsideringWishes(USER_ID))
                .thenReturn(new DashboardWishAggregate(
                        1, new BigDecimal("1000")));

        DashboardResult result = service.summary();

        assertThat(result.itemPlanDailyTotal())
                .isEqualTo("2.74");
        assertThat(result.itemPlanDailyTotalDisplay())
                .isEqualTo("¥2.74/天");
        assertThat(result.itemResidualUnsetCount())
                .isEqualTo(1);
        assertThat(result.subscriptionMonthlyCnyTotal())
                .isEqualTo("150.00");
        assertThat(result.subscriptionMonthlyCnyTotalDisplay())
                .isEqualTo("约 ¥150.00/月");
        assertThat(result.subscriptionMonthlyCnyApproximate())
                .isTrue();
        assertThat(result.subscriptionUnconvertedForeignCount())
                .isEqualTo(1);
        assertThat(result.wishConsideringCount())
                .isEqualTo(1);
        assertThat(result.wishConsideringAmountTotal())
                .isEqualTo("1000.00");
        assertThat(result.wishConsideringAmountTotalDisplay())
                .isEqualTo("¥1000.00");
    }

    private static DashboardItemFact itemFact(
            String purchasePrice,
            String expectedYears,
            String residualValue) {
        return new DashboardItemFact(
                new BigDecimal(purchasePrice),
                new BigDecimal(expectedYears),
                residualValue == null
                        ? null : new BigDecimal(residualValue));
    }

    private static DashboardSubscriptionFact subscriptionFact(
            String amount,
            String currency,
            BillingCycleType billingCycleType,
            Integer billingCycleValue,
            String cnyReferenceAmount) {
        return new DashboardSubscriptionFact(
                new BigDecimal(amount),
                currency,
                billingCycleType,
                billingCycleValue,
                cnyReferenceAmount == null
                        ? null
                        : new BigDecimal(cnyReferenceAmount));
    }
}
