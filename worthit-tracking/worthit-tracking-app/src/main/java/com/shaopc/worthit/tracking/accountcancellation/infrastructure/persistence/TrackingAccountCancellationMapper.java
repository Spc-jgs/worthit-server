package com.shaopc.worthit.tracking.accountcancellation.infrastructure.persistence;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** Tracking 用户写围栏与账号注销物理清理 Mapper。 */
@Mapper
public interface TrackingAccountCancellationMapper {

    @Insert("""
            INSERT INTO trk_user_write_fence (
                user_id, status, cancellation_id, completed_at,
                create_time, update_time
            ) VALUES (
                #{userId}, 'ACTIVE', NULL, NULL, #{now}, #{now}
            )
            ON DUPLICATE KEY UPDATE
                user_id = trk_user_write_fence.user_id
            """)
    int lockOrCreateActive(
            @Param("userId") long userId,
            @Param("now") LocalDateTime now);

    @Select("""
            SELECT user_id, status, cancellation_id, completed_at,
                   create_time, update_time
              FROM trk_user_write_fence
             WHERE user_id = #{userId}
               FOR UPDATE
            """)
    TrackingUserWriteFenceDO selectForUpdate(@Param("userId") long userId);

    @Update("""
            UPDATE trk_user_write_fence
               SET status = 'CANCELLING',
                   cancellation_id = #{cancellationId},
                   completed_at = NULL,
                   update_time = #{now}
             WHERE user_id = #{userId}
               AND status = 'ACTIVE'
            """)
    int beginCancellation(
            @Param("userId") long userId,
            @Param("cancellationId") String cancellationId,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE trk_user_write_fence
               SET status = 'CANCELLED',
                   completed_at = #{now},
                   update_time = #{now}
             WHERE user_id = #{userId}
               AND status = 'CANCELLING'
               AND cancellation_id = #{cancellationId}
            """)
    int completeCancellation(
            @Param("userId") long userId,
            @Param("cancellationId") String cancellationId,
            @Param("now") LocalDateTime now);

    @Delete("DELETE FROM trk_item_replacement WHERE user_id = #{userId}")
    int deleteReplacements(@Param("userId") long userId);

    @Delete("DELETE FROM trk_item_disposal WHERE user_id = #{userId}")
    int deleteDisposals(@Param("userId") long userId);

    @Delete("DELETE FROM trk_outbox_event WHERE user_id = #{userId}")
    int deleteOutbox(@Param("userId") long userId);

    @Delete("DELETE FROM trk_idempotency_record WHERE user_id = #{userId}")
    int deleteIdempotency(@Param("userId") long userId);

    @Delete("DELETE FROM trk_wish WHERE user_id = #{userId}")
    int deleteWishes(@Param("userId") long userId);

    @Delete("DELETE FROM trk_subscription WHERE user_id = #{userId}")
    int deleteSubscriptions(@Param("userId") long userId);

    @Delete("DELETE FROM trk_item WHERE user_id = #{userId}")
    int deleteItems(@Param("userId") long userId);

    @Delete("DELETE FROM trk_category WHERE user_id = #{userId}")
    int deleteCategories(@Param("userId") long userId);
}
