package com.shaopc.worthit.tracking.category.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 分类表 Mapper。
 */
@Mapper
public interface CategoryMapper extends BaseMapper<CategoryDO> {

    /**
     * 检查分类是否被物品、订阅或想买中的有效数据引用。
     *
     * @param categoryId 分类标识
     * @param userId 用户标识
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
                FROM trk_subscription
                WHERE user_id = #{userId}
                  AND category_id = #{categoryId}
                  AND del_flag = 0
                UNION ALL
                SELECT 1
                FROM trk_wish
                WHERE user_id = #{userId}
                  AND category_id = #{categoryId}
                  AND del_flag = 0
                LIMIT 1
            )
            """)
    boolean existsActiveReference(
            @Param("categoryId") long categoryId,
            @Param("userId") long userId);
}
