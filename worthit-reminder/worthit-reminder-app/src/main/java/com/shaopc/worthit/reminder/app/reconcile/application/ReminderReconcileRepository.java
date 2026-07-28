package com.shaopc.worthit.reminder.app.reconcile.application;

import com.shaopc.worthit.reminder.client.command.ReconcileReminderCommand;
import com.shaopc.worthit.reminder.client.model.ReconcileResultCode;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Reminder reconcile 持久化与行锁边界。
 */
public interface ReminderReconcileRepository {

    /**
     * 幂等确保 Binding 存在，并按稳定业务四元组加行锁。
     */
    ReminderBindingState ensureAndLockBinding(
            ReconcileReminderCommand command,
            LocalDateTime now);

    /**
     * 按全局事件标识查询命令历史。
     */
    Optional<ReminderCommandHistory> findCommandByEventId(
            String eventId);

    /**
     * 按 Binding 和来源版本查询权威命令历史。
     */
    Optional<ReminderCommandHistory> findCommandByVersion(
            long bindingId, long sourceVersion);

    /**
     * 记录同版本不同摘要的契约冲突审计。
     */
    void recordConflict(
            long commandId,
            String conflictEventId,
            String conflictDigest,
            LocalDateTime now);

    /**
     * 查询当前唯一 PENDING 实例。
     */
    Optional<ReminderPendingInstance> findPending(
            long bindingId);

    /**
     * 仅在实例仍为 PENDING 时写入终态。
     */
    boolean resolvePending(
            long instanceId,
            String status,
            String resolutionReason,
            String eventId,
            LocalDateTime now);

    /**
     * 创建新的唯一 PENDING 实例。
     */
    void createPending(
            long bindingId,
            String eventId,
            ReconcileReminderCommand command,
            LocalDateTime now);

    /**
     * 推进 Binding 完整期望和来源版本。
     */
    void updateBinding(
            long bindingId,
            boolean reminderEnabled,
            long sourceVersion,
            LocalDateTime now);

    /**
     * 插入本次首次处理的命令日志。
     */
    void insertCommand(
            long bindingId,
            String eventId,
            String payloadDigest,
            ReconcileReminderCommand command,
            ReconcileResultCode resultCode,
            LocalDateTime now);
}
