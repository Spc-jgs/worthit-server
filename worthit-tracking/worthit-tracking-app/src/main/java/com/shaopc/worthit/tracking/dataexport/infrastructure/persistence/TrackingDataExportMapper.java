package com.shaopc.worthit.tracking.dataexport.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 按 user_id、主键升序、有界读取 Tracking 导出数据。
 */
@Mapper
public interface TrackingDataExportMapper {

    @Select("""
            SELECT id, name, system_code, version, create_time, update_time,
                   del_flag, delete_time
              FROM trk_category
             WHERE user_id = #{userId}
             ORDER BY id ASC
             LIMIT #{limit}
            """)
    List<TrackingDataExportRows.CategoryRow> selectCategories(
            @Param("userId") long userId, @Param("limit") int limit);

    @Select("""
            SELECT id, category_id, name, purchase_price, expected_years,
                   residual_value, purchase_date, warranty_expire_date,
                   warranty_reminder_enabled, brand_model, remark,
                   source_wish_id, lifecycle_status, version, create_time,
                   update_time, del_flag, delete_time
              FROM trk_item
             WHERE user_id = #{userId}
             ORDER BY id ASC
             LIMIT #{limit}
            """)
    List<TrackingDataExportRows.ItemRow> selectItems(
            @Param("userId") long userId, @Param("limit") int limit);

    @Select("""
            SELECT id, category_id, name, amount, currency, billing_cycle_type,
                   billing_cycle_value, cny_reference_amount, next_renewal_date,
                   auto_renew, renewal_reminder_enabled, status, remark, version,
                   create_time, update_time, del_flag, delete_time
              FROM trk_subscription
             WHERE user_id = #{userId}
             ORDER BY id ASC
             LIMIT #{limit}
            """)
    List<TrackingDataExportRows.SubscriptionRow> selectSubscriptions(
            @Param("userId") long userId, @Param("limit") int limit);

    @Select("""
            SELECT id, category_id, name, expected_price, expected_years,
                   residual_value, reason, remark, watch_deadline,
                   watch_reminder_enabled, status, last_abandon_reason,
                   last_abandon_at, converted_item_id, version, create_time,
                   update_time, del_flag, delete_time
              FROM trk_wish
             WHERE user_id = #{userId}
             ORDER BY id ASC
             LIMIT #{limit}
            """)
    List<TrackingDataExportRows.WishRow> selectWishes(
            @Param("userId") long userId, @Param("limit") int limit);

    @Select("""
            SELECT id, item_id, disposal_type, disposal_date,
                   purchase_price_snapshot, sale_amount, remark,
                   create_time, update_time
              FROM trk_item_disposal
             WHERE user_id = #{userId}
             ORDER BY id ASC
             LIMIT #{limit}
            """)
    List<TrackingDataExportRows.DisposalRow> selectDisposals(
            @Param("userId") long userId, @Param("limit") int limit);

    @Select("""
            SELECT id, old_item_id, new_item_id, create_time
              FROM trk_item_replacement
             WHERE user_id = #{userId}
             ORDER BY id ASC
             LIMIT #{limit}
            """)
    List<TrackingDataExportRows.ReplacementRow> selectReplacements(
            @Param("userId") long userId, @Param("limit") int limit);
}
