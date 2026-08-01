package com.shaopc.worthit.tracking.wish.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shaopc.worthit.tracking.wish.domain.Wish;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Wish 表 Mapper 与用户隔离查询。
 */
@Mapper
public interface WishMapper extends BaseMapper<WishDO> {

    String VIEW_COLUMNS = """
            w.id, w.user_id, w.category_id,
            c.name AS category_name, w.name,
            w.expected_price, w.expected_years,
            w.residual_value, w.reason, w.remark,
            w.watch_deadline, w.watch_reminder_enabled,
            w.status, w.last_abandon_reason,
            w.last_abandon_at, w.converted_item_id,
            w.conversion_key, w.version,
            w.create_time, w.update_time
            """;

    @Select("""
            SELECT
            """ + VIEW_COLUMNS + """
            FROM trk_wish w
            JOIN trk_category c
              ON c.id = w.category_id
             AND c.user_id = w.user_id
             AND c.del_flag = 0
            WHERE w.id = #{wishId}
              AND w.user_id = #{userId}
              AND w.del_flag = 0
            """)
    WishViewDO selectDetail(
            @Param("wishId") long wishId,
            @Param("userId") long userId);

    @Select("""
            SELECT
            """ + VIEW_COLUMNS + """
            FROM trk_wish w
            JOIN trk_category c
              ON c.id = w.category_id
             AND c.user_id = w.user_id
             AND c.del_flag = 0
            WHERE w.id = #{wishId}
              AND w.user_id = #{userId}
              AND w.del_flag = 0
            FOR UPDATE
            """)
    WishViewDO selectForUpdate(
            @Param("wishId") long wishId,
            @Param("userId") long userId);

    @Select("""
            <script>
            SELECT
            """ + VIEW_COLUMNS + """
            FROM trk_wish w
            JOIN trk_category c
              ON c.id = w.category_id
             AND c.user_id = w.user_id
             AND c.del_flag = 0
            WHERE w.user_id = #{userId}
              AND w.del_flag = 0
            <if test="keyword != null">
              AND w.name LIKE CONCAT('%', #{keyword}, '%')
            </if>
            <if test="categoryId != null">
              AND w.category_id = #{categoryId}
            </if>
            ORDER BY w.create_time DESC, w.id DESC
            LIMIT #{offset}, #{size}
            </script>
            """)
    List<WishViewDO> selectPage(
            @Param("userId") long userId,
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            @Param("offset") long offset,
            @Param("size") int size);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM trk_wish w
            JOIN trk_category c
              ON c.id = w.category_id
             AND c.user_id = w.user_id
             AND c.del_flag = 0
            WHERE w.user_id = #{userId}
              AND w.del_flag = 0
            <if test="keyword != null">
              AND w.name LIKE CONCAT('%', #{keyword}, '%')
            </if>
            <if test="categoryId != null">
              AND w.category_id = #{categoryId}
            </if>
            </script>
            """)
    long countPage(
            @Param("userId") long userId,
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId);

    @Update("""
            UPDATE trk_wish
            SET category_id = #{wish.categoryId},
                name = #{wish.name},
                expected_price = #{wish.expectedPrice},
                expected_years = #{wish.expectedYears},
                residual_value = #{wish.residualValue},
                reason = #{wish.reason},
                remark = #{wish.remark},
                watch_deadline = #{wish.watchDeadline},
                watch_reminder_enabled =
                    #{wish.watchReminderEnabled},
                version = version + 1,
                update_by = #{wish.userId},
                update_time = #{wish.updateTime}
            WHERE id = #{wish.id}
              AND user_id = #{wish.userId}
              AND version = #{expectedVersion}
              AND status = #{expectedStatus}
              AND del_flag = 0
            """)
    int updateByVersion(
            @Param("wish") Wish wish,
            @Param("expectedStatus") String expectedStatus,
            @Param("expectedVersion") long expectedVersion);

    @Update("""
            UPDATE trk_wish
            SET status = #{targetStatus},
                converted_item_id = #{itemId},
                conversion_key = #{conversionKey},
                version = version + 1,
                update_by = #{userId},
                update_time = #{now}
            WHERE id = #{wishId}
              AND user_id = #{userId}
              AND version = #{expectedVersion}
              AND status = #{expectedStatus}
              AND del_flag = 0
            """)
    int purchase(
            @Param("wishId") long wishId,
            @Param("userId") long userId,
            @Param("expectedVersion") long expectedVersion,
            @Param("itemId") long itemId,
            @Param("conversionKey") String conversionKey,
            @Param("expectedStatus") String expectedStatus,
            @Param("targetStatus") String targetStatus,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE trk_wish
            SET status = #{targetStatus},
                last_abandon_reason = #{abandonReason},
                last_abandon_at = #{abandonAt},
                version = version + 1,
                update_by = #{userId},
                update_time = #{now}
            WHERE id = #{wishId}
              AND user_id = #{userId}
              AND version = #{expectedVersion}
              AND status = #{expectedStatus}
              AND del_flag = 0
            """)
    int changeStatus(
            @Param("wishId") long wishId,
            @Param("userId") long userId,
            @Param("expectedVersion") long expectedVersion,
            @Param("expectedStatus") String expectedStatus,
            @Param("targetStatus") String targetStatus,
            @Param("abandonReason") String abandonReason,
            @Param("abandonAt") LocalDateTime abandonAt,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE trk_wish
            SET del_flag = 1, delete_time = #{now},
                version = version + 1,
                update_by = #{userId}, update_time = #{now}
            WHERE id = #{wishId}
              AND user_id = #{userId}
              AND version = #{expectedVersion}
              AND del_flag = 0
            """)
    int deleteByVersion(
            @Param("wishId") long wishId,
            @Param("userId") long userId,
            @Param("expectedVersion") long expectedVersion,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE trk_wish
            SET del_flag = 0, delete_time = NULL,
                version = version + 1,
                update_by = #{userId}, update_time = #{now}
            WHERE id = #{wishId}
              AND user_id = #{userId}
              AND version = #{deletedVersion}
              AND del_flag = 1
            """)
    int restoreByVersion(
            @Param("wishId") long wishId,
            @Param("userId") long userId,
            @Param("deletedVersion") long deletedVersion,
            @Param("now") LocalDateTime now);

    /** 完整恢复想买并原子写入有效目标分类。 */
    @Update("""
            UPDATE trk_wish
            SET category_id = #{categoryId},
                del_flag = 0, delete_time = NULL,
                version = version + 1,
                update_by = #{userId}, update_time = #{now}
            WHERE id = #{wishId}
              AND user_id = #{userId}
              AND version = #{deletedVersion}
              AND del_flag = 1
            """)
    int restoreByVersionToCategory(
            @Param("wishId") long wishId,
            @Param("userId") long userId,
            @Param("deletedVersion") long deletedVersion,
            @Param("categoryId") long categoryId,
            @Param("now") LocalDateTime now);
}
