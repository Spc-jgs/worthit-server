package com.shaopc.worthit.reminder.app.reconcile.infrastructure.persistence;

import com.shaopc.worthit.reminder.client.command.ReconcileReminderCommand;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * Reminder reconcile 行锁、实例和命令日志 Mapper。
 */
@Mapper
public interface ReminderReconcileMapper {

    /**
     * 并发安全地确保稳定 Binding 存在。
     */
    @Insert("""
            INSERT INTO rem_binding (
              id, user_id, business_type, business_id,
              reminder_type, reminder_enabled,
              last_source_version, create_time, update_time
            ) VALUES (
              #{id}, #{command.userId},
              #{command.businessType}, #{command.businessId},
              #{command.reminderType}, 0, 0, #{now}, #{now}
            )
            ON DUPLICATE KEY UPDATE id = id
            """)
    int ensureBinding(
            @Param("id") long id,
            @Param("command") ReconcileReminderCommand command,
            @Param("now") LocalDateTime now);

    /**
     * 按业务四元组锁定 Binding。
     */
    @Select("""
            SELECT id, reminder_enabled, last_source_version
            FROM rem_binding
            WHERE user_id = #{command.userId}
              AND business_type = #{command.businessType}
              AND business_id = #{command.businessId}
              AND reminder_type = #{command.reminderType}
            FOR UPDATE
            """)
    ReminderBindingDO selectBindingForUpdate(
            @Param("command") ReconcileReminderCommand command);

    /**
     * 按 event_id 查询命令历史。
     */
    @Select("""
            SELECT id, event_id, binding_id, source_version,
                   payload_digest, result_code
            FROM rem_command_log
            WHERE event_id = #{eventId}
            """)
    ReminderCommandLogDO selectCommandByEventId(
            @Param("eventId") String eventId);

    /**
     * 按 Binding 和来源版本查询权威历史。
     */
    @Select("""
            SELECT id, event_id, binding_id, source_version,
                   payload_digest, result_code
            FROM rem_command_log
            WHERE binding_id = #{bindingId}
              AND source_version = #{sourceVersion}
            """)
    ReminderCommandLogDO selectCommandByVersion(
            @Param("bindingId") long bindingId,
            @Param("sourceVersion") long sourceVersion);

    /**
     * 增加权威命令的冲突审计。
     */
    @Update("""
            UPDATE rem_command_log
            SET conflict_count = conflict_count + 1,
                last_conflict_event_id = #{eventId},
                last_conflict_digest = #{digest},
                last_conflict_at = #{now},
                update_time = #{now}
            WHERE id = #{commandId}
            """)
    int recordConflict(
            @Param("commandId") long commandId,
            @Param("eventId") String eventId,
            @Param("digest") String digest,
            @Param("now") LocalDateTime now);

    /**
     * 查询当前唯一 PENDING 实例。
     */
    @Select("""
            SELECT id, remind_at
            FROM rem_instance
            WHERE binding_id = #{bindingId}
              AND status = 'PENDING'
            FOR UPDATE
            """)
    ReminderInstanceDO selectPendingForUpdate(
            @Param("bindingId") long bindingId);

    /**
     * 条件归档当前 PENDING 实例。
     */
    @Update("""
            UPDATE rem_instance
            SET status = #{status},
                resolved_at = #{now},
                resolution_reason = #{reason},
                resolved_source_event_id = #{eventId},
                update_time = #{now}
            WHERE id = #{instanceId}
              AND status = 'PENDING'
            """)
    int resolvePending(
            @Param("instanceId") long instanceId,
            @Param("status") String status,
            @Param("reason") String reason,
            @Param("eventId") String eventId,
            @Param("now") LocalDateTime now);

    /**
     * 创建新的 PENDING 实例。
     */
    @Insert("""
            INSERT INTO rem_instance (
              id, binding_id, user_id, business_date,
              remind_at, timezone, status,
              created_source_event_id, create_time, update_time
            ) VALUES (
              #{id}, #{bindingId}, #{command.userId},
              #{command.businessDate}, #{command.remindAt},
              'Asia/Shanghai', 'PENDING',
              #{eventId}, #{now}, #{now}
            )
            """)
    int insertPending(
            @Param("id") long id,
            @Param("bindingId") long bindingId,
            @Param("eventId") String eventId,
            @Param("command") ReconcileReminderCommand command,
            @Param("now") LocalDateTime now);

    /**
     * 推进 Binding 完整期望与来源版本。
     */
    @Update("""
            UPDATE rem_binding
            SET reminder_enabled = #{enabled},
                last_source_version = #{sourceVersion},
                update_time = #{now}
            WHERE id = #{bindingId}
            """)
    int updateBinding(
            @Param("bindingId") long bindingId,
            @Param("enabled") boolean enabled,
            @Param("sourceVersion") long sourceVersion,
            @Param("now") LocalDateTime now);

    /**
     * 插入首次处理的权威命令日志。
     */
    @Insert("""
            INSERT INTO rem_command_log (
              id, event_id, binding_id, source_version,
              schema_version, payload_digest, operation_type,
              result_code, conflict_count, create_time, update_time
            ) VALUES (
              #{id}, #{eventId}, #{bindingId},
              #{command.sourceVersion}, #{command.schemaVersion},
              #{digest}, #{command.operationType},
              #{resultCode}, 0, #{now}, #{now}
            )
            """)
    int insertCommand(
            @Param("id") long id,
            @Param("bindingId") long bindingId,
            @Param("eventId") String eventId,
            @Param("digest") String digest,
            @Param("command") ReconcileReminderCommand command,
            @Param("resultCode") String resultCode,
            @Param("now") LocalDateTime now);
}
