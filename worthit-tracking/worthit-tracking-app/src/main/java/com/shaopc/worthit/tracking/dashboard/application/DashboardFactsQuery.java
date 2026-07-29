package com.shaopc.worthit.tracking.dashboard.application;

import java.util.List;

/**
 * 为 Dashboard 提供当前用户最小事实投影的只读端口。
 */
public interface DashboardFactsQuery {

    /**
     * 查询当前持有且未删除的物品成本事实。
     *
     * @param userId 当前用户标识
     * @return 物品成本事实
     */
    List<DashboardItemFact> findHoldingItems(long userId);

    /**
     * 查询当前有效且未删除的订阅成本事实。
     *
     * @param userId 当前用户标识
     * @return 订阅成本事实
     */
    List<DashboardSubscriptionFact> findActiveSubscriptions(
            long userId);

    /**
     * 汇总当前考虑中且未删除的想买事实。
     *
     * @param userId 当前用户标识
     * @return 想买数量与预计金额
     */
    DashboardWishAggregate aggregateConsideringWishes(
            long userId);
}
