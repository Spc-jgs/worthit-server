package com.shaopc.worthit.tracking.dashboard.infrastructure.persistence;

import com.shaopc.worthit.tracking.dashboard.application.DashboardFactsQuery;
import com.shaopc.worthit.tracking.dashboard.application.DashboardItemFact;
import com.shaopc.worthit.tracking.dashboard.application.DashboardSubscriptionFact;
import com.shaopc.worthit.tracking.dashboard.application.DashboardWishAggregate;
import com.shaopc.worthit.tracking.item.domain.ItemLifecycleStatus;
import com.shaopc.worthit.tracking.subscription.domain.BillingCycleType;
import com.shaopc.worthit.tracking.subscription.domain.SubscriptionStatus;
import com.shaopc.worthit.tracking.wish.domain.WishStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 使用 MyBatis 实现 Dashboard 最小事实查询端口。
 */
@Repository
@RequiredArgsConstructor
public class MybatisDashboardFactsQuery
        implements DashboardFactsQuery {

    private final DashboardMapper dashboardMapper;

    @Override
    public List<DashboardItemFact> findHoldingItems(
            long userId) {
        return dashboardMapper.selectHoldingItems(
                        userId,
                        ItemLifecycleStatus.HOLDING.code())
                .stream()
                .map(row -> new DashboardItemFact(
                        row.getPurchasePrice(),
                        row.getExpectedYears(),
                        row.getResidualValue()))
                .toList();
    }

    @Override
    public List<DashboardSubscriptionFact>
            findActiveSubscriptions(long userId) {
        return dashboardMapper.selectActiveSubscriptions(
                        userId,
                        SubscriptionStatus.ACTIVE.code())
                .stream()
                .map(row -> new DashboardSubscriptionFact(
                        row.getAmount(),
                        row.getCurrency(),
                        BillingCycleType.fromCode(
                                row.getBillingCycleType()),
                        row.getBillingCycleValue(),
                        row.getCnyReferenceAmount()))
                .toList();
    }

    @Override
    public DashboardWishAggregate aggregateConsideringWishes(
            long userId) {
        DashboardWishAggregateDO aggregate =
                dashboardMapper
                        .selectConsideringWishAggregate(
                                userId,
                                WishStatus.CONSIDERING.code());
        return new DashboardWishAggregate(
                aggregate.getCount(),
                aggregate.getAmountTotal());
    }
}
