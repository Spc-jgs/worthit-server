package com.shaopc.worthit.tracking.dashboard.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Dashboard 当前用户只读事实查询。
 */
@Mapper
public interface DashboardMapper {

    /**
     * 查询持有中且未删除物品的成本事实。
     */
    @Select("""
            SELECT purchase_price,
                   expected_years,
                   residual_value
            FROM trk_item
            WHERE user_id = #{userId}
              AND del_flag = 0
              AND lifecycle_status = 'HOLDING'
            """)
    List<DashboardItemFactDO> selectHoldingItems(
            @Param("userId") long userId);

    /**
     * 查询有效且未删除订阅的成本事实。
     */
    @Select("""
            SELECT amount,
                   currency,
                   billing_cycle_type,
                   billing_cycle_value,
                   cny_reference_amount
            FROM trk_subscription
            WHERE user_id = #{userId}
              AND del_flag = 0
              AND status = 'ACTIVE'
            """)
    List<DashboardSubscriptionFactDO> selectActiveSubscriptions(
            @Param("userId") long userId);

    /**
     * 汇总考虑中且未删除想买的数量和预计金额。
     */
    @Select("""
            SELECT COUNT(*) AS count,
                   COALESCE(SUM(expected_price), 0)
                       AS amount_total
            FROM trk_wish
            WHERE user_id = #{userId}
              AND del_flag = 0
              AND status = 'CONSIDERING'
            """)
    DashboardWishAggregateDO selectConsideringWishAggregate(
            @Param("userId") long userId);
}
