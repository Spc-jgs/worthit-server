package com.shaopc.worthit.tracking.dashboard.application;

import com.shaopc.worthit.tracking.item.domain.ItemCostCalculator;
import com.shaopc.worthit.tracking.security.CurrentUserProvider;
import com.shaopc.worthit.tracking.subscription.domain.SubscriptionCostCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 编排当前用户 Dashboard 实时成本汇总。
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final String CNY = "CNY";

    private final CurrentUserProvider currentUserProvider;
    private final DashboardFactsQuery factsQuery;

    /**
     * 在同一只读事务快照中汇总三类 Tracking 事实。
     *
     * @return Dashboard 完整应用结果
     */
    @Transactional(readOnly = true)
    public DashboardResult summary() {
        long userId = currentUserProvider.currentUser().userId();
        ItemTotals itemTotals = itemTotals(userId);
        SubscriptionTotals subscriptionTotals =
                subscriptionTotals(userId);
        DashboardWishAggregate wishes =
                factsQuery.aggregateConsideringWishes(userId);
        BigDecimal wishAmount = money(wishes.amountTotal());

        return new DashboardResult(
                itemTotals.rounded().toPlainString(),
                dailyDisplay(
                        itemTotals.exact(),
                        itemTotals.rounded()),
                itemTotals.residualUnsetCount(),
                subscriptionTotals.rounded().toPlainString(),
                monthlyDisplay(subscriptionTotals),
                subscriptionTotals.approximate(),
                subscriptionTotals.unconvertedCount(),
                wishes.count(),
                wishAmount.toPlainString(),
                "¥" + wishAmount.toPlainString());
    }

    private ItemTotals itemTotals(long userId) {
        BigDecimal exact = BigDecimal.ZERO;
        long residualUnsetCount = 0;
        for (DashboardItemFact item
                : factsQuery.findHoldingItems(userId)) {
            exact = exact.add(
                    ItemCostCalculator
                            .calculateExactPlanDailyCost(
                                    item.purchasePrice(),
                                    item.expectedYears(),
                                    item.residualValue()));
            if (item.residualValue() == null) {
                residualUnsetCount++;
            }
        }
        return new ItemTotals(
                exact, money(exact), residualUnsetCount);
    }

    private SubscriptionTotals subscriptionTotals(
            long userId) {
        BigDecimal exact = BigDecimal.ZERO;
        long unconvertedCount = 0;
        boolean approximate = false;
        for (DashboardSubscriptionFact subscription
                : factsQuery.findActiveSubscriptions(userId)) {
            boolean cny = CNY.equals(subscription.currency());
            if (!cny
                    && subscription.cnyReferenceAmount() == null) {
                unconvertedCount++;
                continue;
            }
            BigDecimal amount = cny
                    ? subscription.amount()
                    : subscription.cnyReferenceAmount();
            exact = exact.add(
                    SubscriptionCostCalculator.normalizeMonthly(
                            amount,
                            subscription.billingCycleType(),
                            subscription.billingCycleValue()));
            approximate = approximate || !cny;
        }
        return new SubscriptionTotals(
                money(exact),
                approximate,
                unconvertedCount);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String dailyDisplay(
            BigDecimal exact,
            BigDecimal rounded) {
        if (exact.signum() > 0 && rounded.signum() == 0) {
            return "<¥0.01/天";
        }
        return "¥" + rounded.toPlainString() + "/天";
    }

    private static String monthlyDisplay(
            SubscriptionTotals totals) {
        return (totals.approximate() ? "约 " : "")
                + "¥" + totals.rounded().toPlainString() + "/月";
    }

    private record ItemTotals(
            BigDecimal exact,
            BigDecimal rounded,
            long residualUnsetCount) {
    }

    private record SubscriptionTotals(
            BigDecimal rounded,
            boolean approximate,
            long unconvertedCount) {
    }
}
