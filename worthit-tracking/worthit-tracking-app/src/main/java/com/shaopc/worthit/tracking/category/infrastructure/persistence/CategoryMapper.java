package com.shaopc.worthit.tracking.category.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * 分类表 Mapper。
 */
@Mapper
public interface CategoryMapper extends BaseMapper<CategoryDO> {

    @Select("""
            SELECT *
            FROM trk_category
            WHERE id = #{categoryId}
              AND user_id = #{userId}
              AND del_flag = 0
            FOR UPDATE
            """)
    CategoryDO selectByIdAndUserIdForUpdate(
            @Param("categoryId") long categoryId,
            @Param("userId") long userId);

    @Select("""
            SELECT *
            FROM trk_category
            WHERE id = #{categoryId}
              AND user_id = #{userId}
              AND system_code IS NULL
              AND del_flag = 0
            FOR UPDATE
            """)
    CategoryDO selectCustomByIdAndUserIdForUpdate(
            @Param("categoryId") long categoryId,
            @Param("userId") long userId);

    /**
     * 检查分类是否被物品、订阅或想买中的有效或可恢复数据引用。
     *
     * @param categoryId 分类标识
     * @param userId 用户标识
     * @param earliestRestorableDeletion 可恢复删除时间下界
     * @return 有引用时为 true
     */
    @Select("""
            SELECT EXISTS(
                SELECT 1
                FROM trk_item
                WHERE user_id = #{userId}
                  AND category_id = #{categoryId}
                  AND del_flag = 0
                UNION ALL
                SELECT 1
                FROM trk_item
                WHERE user_id = #{userId}
                  AND category_id = #{categoryId}
                  AND del_flag = 1
                  AND delete_time >= #{earliestRestorableDeletion}
                UNION ALL
                SELECT 1
                FROM trk_subscription
                WHERE user_id = #{userId}
                  AND category_id = #{categoryId}
                  AND del_flag = 0
                UNION ALL
                SELECT 1
                FROM trk_subscription
                WHERE user_id = #{userId}
                  AND category_id = #{categoryId}
                  AND del_flag = 1
                  AND delete_time >= #{earliestRestorableDeletion}
                UNION ALL
                SELECT 1
                FROM trk_wish
                WHERE user_id = #{userId}
                  AND category_id = #{categoryId}
                  AND del_flag = 0
                UNION ALL
                SELECT 1
                FROM trk_wish
                WHERE user_id = #{userId}
                  AND category_id = #{categoryId}
                  AND del_flag = 1
                  AND delete_time >= #{earliestRestorableDeletion}
                LIMIT 1
            )
            """)
    boolean existsReferenceWithinRestoreWindow(
            @Param("categoryId") long categoryId,
            @Param("userId") long userId,
            @Param("earliestRestorableDeletion")
            LocalDateTime earliestRestorableDeletion);
}
