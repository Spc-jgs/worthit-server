package com.shaopc.worthit.reminder.app.reconcile.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.shaopc.worthit.reminder.client.command.ReconcileReminderCommand;
import com.shaopc.worthit.reminder.client.model.ReconcileResultCode;
import com.shaopc.worthit.reminder.app.reconcile.application.ReminderBindingState;
import com.shaopc.worthit.reminder.app.reconcile.application.ReminderCommandHistory;
import com.shaopc.worthit.reminder.app.reconcile.application.ReminderPendingInstance;
import com.shaopc.worthit.reminder.app.reconcile.application.ReminderReconcileRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 基于 MySQL 唯一键与 Binding 行锁实现 reconcile 持久化。
 */
@Repository
public class MybatisReminderReconcileRepository
        implements ReminderReconcileRepository {

    private final ReminderReconcileMapper mapper;

    /**
     * 创建 MyBatis reconcile 仓储。
     */
    public MybatisReminderReconcileRepository(
            ReminderReconcileMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ReminderBindingState ensureAndLockBinding(
            ReconcileReminderCommand command,
            LocalDateTime now) {
        mapper.ensureBinding(IdWorker.getId(), command, now);
        ReminderBindingDO binding =
                mapper.selectBindingForUpdate(command);
        if (binding == null) {
            throw new IllegalStateException(
                    "Reminder Binding创建后无法锁定");
        }
        return new ReminderBindingState(
                binding.getId(),
                Boolean.TRUE.equals(
                        binding.getReminderEnabled()),
                binding.getLastSourceVersion());
    }

    @Override
    public Optional<ReminderCommandHistory> findCommandByEventId(
            String eventId) {
        return Optional.ofNullable(
                        mapper.selectCommandByEventId(eventId))
                .map(this::toHistory);
    }

    @Override
    public Optional<ReminderCommandHistory> findCommandByVersion(
            long bindingId, long sourceVersion) {
        return Optional.ofNullable(
                        mapper.selectCommandByVersion(
                                bindingId, sourceVersion))
                .map(this::toHistory);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordConflict(
            long commandId,
            String conflictEventId,
            String conflictDigest,
            LocalDateTime now) {
        if (mapper.recordConflict(
                commandId,
                conflictEventId,
                conflictDigest,
                now) != 1) {
            throw new IllegalStateException(
                    "Reminder命令冲突审计写入失败");
        }
    }

    @Override
    public Optional<ReminderPendingInstance> findPending(
            long bindingId) {
        return Optional.ofNullable(
                        mapper.selectPendingForUpdate(bindingId))
                .map(instance -> new ReminderPendingInstance(
                        instance.getId(),
                        instance.getRemindAt()));
    }

    @Override
    public boolean resolvePending(
            long instanceId,
            String status,
            String resolutionReason,
            String eventId,
            LocalDateTime now) {
        return mapper.resolvePending(
                instanceId,
                status,
                resolutionReason,
                eventId,
                now) == 1;
    }

    @Override
    public void createPending(
            long bindingId,
            String eventId,
            ReconcileReminderCommand command,
            LocalDateTime now) {
        if (mapper.insertPending(
                IdWorker.getId(),
                bindingId,
                eventId,
                command,
                now) != 1) {
            throw new IllegalStateException(
                    "Reminder PENDING实例创建失败");
        }
    }

    @Override
    public void updateBinding(
            long bindingId,
            boolean reminderEnabled,
            long sourceVersion,
            LocalDateTime now) {
        if (mapper.updateBinding(
                bindingId,
                reminderEnabled,
                sourceVersion,
                now) != 1) {
            throw new IllegalStateException(
                    "Reminder Binding推进失败");
        }
    }

    @Override
    public void insertCommand(
            long bindingId,
            String eventId,
            String payloadDigest,
            ReconcileReminderCommand command,
            ReconcileResultCode resultCode,
            LocalDateTime now) {
        if (mapper.insertCommand(
                IdWorker.getId(),
                bindingId,
                eventId,
                payloadDigest,
                command,
                resultCode.name(),
                now) != 1) {
            throw new IllegalStateException(
                    "Reminder命令日志写入失败");
        }
    }

    private ReminderCommandHistory toHistory(
            ReminderCommandLogDO data) {
        return new ReminderCommandHistory(
                data.getId(),
                data.getEventId(),
                data.getBindingId(),
                data.getSourceVersion(),
                data.getPayloadDigest(),
                ReconcileResultCode.valueOf(
                        data.getResultCode()));
    }
}
