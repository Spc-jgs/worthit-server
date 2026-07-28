package com.shaopc.worthit.reminder.app.reminder.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Reminder 公网列表、计数和忽略 Mapper。
 */
@Mapper
public interface ReminderViewMapper {

    @Select("""
            SELECT i.id, b.reminder_type, b.business_type,
                   b.business_id, i.business_date,
                   i.remind_at, i.status
            FROM rem_instance i
            JOIN rem_binding b ON b.id = i.binding_id
            WHERE i.user_id = #{userId}
              AND i.status = 'PENDING'
              AND i.remind_at <= #{now}
            ORDER BY i.remind_at ASC, i.id ASC
            LIMIT #{offset}, #{size}
            """)
    List<ReminderListRowDO> selectPendingPage(
            @Param("userId") long userId,
            @Param("now") LocalDateTime now,
            @Param("offset") long offset,
            @Param("size") int size);

    @Select("""
            SELECT COUNT(*)
            FROM rem_instance
            WHERE user_id = #{userId}
              AND status = 'PENDING'
              AND remind_at <= #{now}
            """)
    long countPending(
            @Param("userId") long userId,
            @Param("now") LocalDateTime now);

    @Select("""
            SELECT i.id, b.reminder_type, b.business_type,
                   b.business_id, i.business_date,
                   i.remind_at, i.status
            FROM rem_instance i
            JOIN rem_binding b ON b.id = i.binding_id
            WHERE i.user_id = #{userId}
              AND i.status IN ('PROCESSED', 'IGNORED')
            ORDER BY i.resolved_at DESC, i.id DESC
            LIMIT #{offset}, #{size}
            """)
    List<ReminderListRowDO> selectDonePage(
            @Param("userId") long userId,
            @Param("offset") long offset,
            @Param("size") int size);

    @Select("""
            SELECT COUNT(*)
            FROM rem_instance
            WHERE user_id = #{userId}
              AND status IN ('PROCESSED', 'IGNORED')
            """)
    long countDone(@Param("userId") long userId);

    @Select("""
            SELECT id, status, remind_at
            FROM rem_instance
            WHERE id = #{reminderId}
              AND user_id = #{userId}
            FOR UPDATE
            """)
    ReminderPublicInstanceDO selectByIdForUpdate(
            @Param("userId") long userId,
            @Param("reminderId") long reminderId);

    @Update("""
            UPDATE rem_instance
            SET status = 'IGNORED',
                resolved_at = #{now},
                resolution_reason = 'USER_IGNORED',
                update_time = #{now}
            WHERE id = #{reminderId}
              AND user_id = #{userId}
              AND status = 'PENDING'
              AND remind_at <= #{now}
            """)
    int ignore(
            @Param("userId") long userId,
            @Param("reminderId") long reminderId,
            @Param("now") LocalDateTime now);
}
