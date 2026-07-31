package com.shaopc.worthit.tracking.lifecycle.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 生命周期复盘联合读模型 Mapper。
 */
@Mapper
public interface LifecycleReviewMapper {

    /**
     * 查询一页处置与替换事实。
     */
    @Select("""
            SELECT review.id, review.entry_type,
                   review.event_date, review.create_time,
                   review.item_id, review.item_name,
                   review.disposal_type,
                   review.disposal_date,
                   review.sale_amount,
                   review.purchase_price_snapshot,
                   review.old_item_id,
                   review.old_item_name,
                   review.new_item_id,
                   review.new_item_name
            FROM (
                SELECT d.id AS id,
                       'DISPOSAL' AS entry_type,
                       d.disposal_date AS event_date,
                       d.create_time AS create_time,
                       i.id AS item_id,
                       i.name AS item_name,
                       d.disposal_type AS disposal_type,
                       d.disposal_date AS disposal_date,
                       d.sale_amount AS sale_amount,
                       d.purchase_price_snapshot
                           AS purchase_price_snapshot,
                       NULL AS old_item_id,
                       NULL AS old_item_name,
                       NULL AS new_item_id,
                       NULL AS new_item_name
                FROM trk_item_disposal d
                JOIN trk_item i
                  ON i.id = d.item_id
                 AND i.user_id = d.user_id
                WHERE d.user_id = #{userId}
                UNION ALL
                SELECT r.id AS id,
                       'REPLACEMENT' AS entry_type,
                       DATE(r.create_time) AS event_date,
                       r.create_time AS create_time,
                       NULL AS item_id,
                       NULL AS item_name,
                       NULL AS disposal_type,
                       NULL AS disposal_date,
                       NULL AS sale_amount,
                       NULL AS purchase_price_snapshot,
                       old_item.id AS old_item_id,
                       old_item.name AS old_item_name,
                       new_item.id AS new_item_id,
                       new_item.name AS new_item_name
                FROM trk_item_replacement r
                JOIN trk_item old_item
                  ON old_item.id = r.old_item_id
                 AND old_item.user_id = r.user_id
                JOIN trk_item new_item
                  ON new_item.id = r.new_item_id
                 AND new_item.user_id = r.user_id
                WHERE r.user_id = #{userId}
            ) review
            ORDER BY review.event_date DESC,
                     review.create_time DESC,
                     review.id DESC
            LIMIT #{offset}, #{size}
            """)
    List<LifecycleReviewRow> selectPage(
            @Param("userId") long userId,
            @Param("offset") long offset,
            @Param("size") int size);

    /**
     * 统计用户全部生命周期事实。
     */
    @Select("""
            SELECT
                (SELECT COUNT(*)
                 FROM trk_item_disposal
                 WHERE user_id = #{userId})
              + (SELECT COUNT(*)
                 FROM trk_item_replacement
                 WHERE user_id = #{userId})
            """)
    long countAll(@Param("userId") long userId);
}
