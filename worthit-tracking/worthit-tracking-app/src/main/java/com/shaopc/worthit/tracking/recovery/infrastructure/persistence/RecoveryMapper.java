package com.shaopc.worthit.tracking.recovery.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 三类已删除资源的统一只读投影 Mapper。
 */
@Mapper
public interface RecoveryMapper {

    /**
     * 查询当前用户已删除资源的稳定分页。
     */
    @Select("""
            <script>
            SELECT r.id, r.resource_type, r.name,
                   r.category_id, r.category_name,
                   r.category_available, r.status,
                   r.version, r.deleted_at
            FROM (
              SELECT i.id, 'ITEM' AS resource_type,
                     i.name, i.category_id,
                     c.name AS category_name,
                     CASE WHEN c.id IS NOT NULL
                               AND c.del_flag = 0
                          THEN TRUE ELSE FALSE END
                         AS category_available,
                     i.lifecycle_status AS status,
                     i.version, i.delete_time AS deleted_at
              FROM trk_item i
              LEFT JOIN trk_category c
                ON c.id = i.category_id
               AND c.user_id = i.user_id
              WHERE i.user_id = #{userId}
                AND i.del_flag = 1
              UNION ALL
              SELECT s.id, 'SUBSCRIPTION' AS resource_type,
                     s.name, s.category_id,
                     c.name AS category_name,
                     CASE WHEN c.id IS NOT NULL
                               AND c.del_flag = 0
                          THEN TRUE ELSE FALSE END
                         AS category_available,
                     s.status, s.version,
                     s.delete_time AS deleted_at
              FROM trk_subscription s
              LEFT JOIN trk_category c
                ON c.id = s.category_id
               AND c.user_id = s.user_id
              WHERE s.user_id = #{userId}
                AND s.del_flag = 1
              UNION ALL
              SELECT w.id, 'WISH' AS resource_type,
                     w.name, w.category_id,
                     c.name AS category_name,
                     CASE WHEN c.id IS NOT NULL
                               AND c.del_flag = 0
                          THEN TRUE ELSE FALSE END
                         AS category_available,
                     w.status, w.version,
                     w.delete_time AS deleted_at
              FROM trk_wish w
              LEFT JOIN trk_category c
                ON c.id = w.category_id
               AND c.user_id = w.user_id
              WHERE w.user_id = #{userId}
                AND w.del_flag = 1
            ) r
            <if test="resourceType != null">
              WHERE r.resource_type = #{resourceType}
            </if>
            ORDER BY r.deleted_at DESC,
                     r.resource_type ASC, r.id DESC
            LIMIT #{offset}, #{size}
            </script>
            """)
    List<RecoveryResourceDO> selectDeletedPage(
            @Param("userId") long userId,
            @Param("resourceType") String resourceType,
            @Param("offset") long offset,
            @Param("size") int size);

    /**
     * 统计当前用户已删除资源。
     */
    @Select("""
            <script>
            SELECT COUNT(*)
            FROM (
              SELECT i.id, 'ITEM' AS resource_type
              FROM trk_item i
              WHERE i.user_id = #{userId}
                AND i.del_flag = 1
              UNION ALL
              SELECT s.id, 'SUBSCRIPTION' AS resource_type
              FROM trk_subscription s
              WHERE s.user_id = #{userId}
                AND s.del_flag = 1
              UNION ALL
              SELECT w.id, 'WISH' AS resource_type
              FROM trk_wish w
              WHERE w.user_id = #{userId}
                AND w.del_flag = 1
            ) r
            <if test="resourceType != null">
              WHERE r.resource_type = #{resourceType}
            </if>
            </script>
            """)
    long countDeleted(
            @Param("userId") long userId,
            @Param("resourceType") String resourceType);
}
