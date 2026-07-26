package com.shaopc.worthit.tracking.item.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Item 表 Mapper 与用户隔离查询。
 */
@Mapper
public interface ItemMapper extends BaseMapper<ItemDO> {

    /**
     * 查询用户有效物品详情。
     */
    @Select("""
            SELECT i.id, i.user_id, i.category_id,
                   c.name AS category_name, i.name,
                   i.purchase_price, i.expected_years,
                   i.residual_value, i.purchase_date,
                   i.warranty_expire_date,
                   i.warranty_reminder_enabled,
                   i.brand_model, i.remark,
                   i.lifecycle_status, i.version,
                   i.create_time, i.update_time
            FROM trk_item i
            JOIN trk_category c
              ON c.id = i.category_id
             AND c.user_id = i.user_id
             AND c.del_flag = 0
            WHERE i.id = #{itemId}
              AND i.user_id = #{userId}
              AND i.del_flag = 0
            """)
    ItemViewDO selectDetail(
            @Param("itemId") long itemId,
            @Param("userId") long userId);

    /**
     * 分页查询用户有效物品。
     */
    @Select("""
            <script>
            SELECT i.id, i.user_id, i.category_id,
                   c.name AS category_name, i.name,
                   i.purchase_price, i.expected_years,
                   i.residual_value, i.purchase_date,
                   i.warranty_expire_date,
                   i.warranty_reminder_enabled,
                   i.brand_model, i.remark,
                   i.lifecycle_status, i.version,
                   i.create_time, i.update_time
            FROM trk_item i
            JOIN trk_category c
              ON c.id = i.category_id
             AND c.user_id = i.user_id
             AND c.del_flag = 0
            WHERE i.user_id = #{userId}
              AND i.del_flag = 0
            <if test="keyword != null">
              AND i.name LIKE CONCAT('%', #{keyword}, '%')
            </if>
            <if test="categoryId != null">
              AND i.category_id = #{categoryId}
            </if>
            ORDER BY i.create_time DESC, i.id DESC
            LIMIT #{offset}, #{size}
            </script>
            """)
    List<ItemViewDO> selectPage(
            @Param("userId") long userId,
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            @Param("offset") long offset,
            @Param("size") int size);

    /**
     * 统计用户有效物品。
     */
    @Select("""
            <script>
            SELECT COUNT(*)
            FROM trk_item i
            WHERE i.user_id = #{userId}
              AND i.del_flag = 0
            <if test="keyword != null">
              AND i.name LIKE CONCAT('%', #{keyword}, '%')
            </if>
            <if test="categoryId != null">
              AND i.category_id = #{categoryId}
            </if>
            </script>
            """)
    long countPage(
            @Param("userId") long userId,
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId);
}
