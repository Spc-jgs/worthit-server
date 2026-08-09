package com.shaopc.worthit.reminder.app.accountcancellation.infrastructure.persistence;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** Reminder 用户写围栏与账号注销物理清理 Mapper。 */
@Mapper
public interface ReminderAccountCancellationMapper {

    @Insert("""
            INSERT INTO rem_user_write_fence (
                user_id, status, cancellation_id, completed_at,
                create_time, update_time
            ) VALUES (
                #{userId}, 'ACTIVE', NULL, NULL, #{now}, #{now}
            )
            ON DUPLICATE KEY UPDATE
                user_id = rem_user_write_fence.user_id
            """)
    int lockOrCreateActive(
            @Param("userId") long userId,
            @Param("now") LocalDateTime now);

    @Select("""
            SELECT user_id, status, cancellation_id, completed_at,
                   create_time, update_time
              FROM rem_user_write_fence
             WHERE user_id = #{userId}
               FOR UPDATE
            """)
    ReminderUserWriteFenceDO selectForUpdate(@Param("userId") long userId);

    @Update("""
            UPDATE rem_user_write_fence
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
            UPDATE rem_user_write_fence
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

    @Delete("""
            DELETE command_log
              FROM rem_command_log command_log
              JOIN rem_binding binding ON binding.id = command_log.binding_id
             WHERE binding.user_id = #{userId}
            """)
    int deleteCommandLogs(@Param("userId") long userId);

    @Delete("DELETE FROM rem_instance WHERE user_id = #{userId}")
    int deleteInstances(@Param("userId") long userId);

    @Delete("DELETE FROM rem_binding WHERE user_id = #{userId}")
    int deleteBindings(@Param("userId") long userId);
}
