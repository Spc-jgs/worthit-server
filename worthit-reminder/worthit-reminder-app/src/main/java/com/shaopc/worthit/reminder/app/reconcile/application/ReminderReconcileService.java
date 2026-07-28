package com.shaopc.worthit.reminder.app.reconcile.application;

import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.web.error.CommonWebErrorCode;
import com.shaopc.worthit.reminder.app.reconcile.domain.ReminderErrorCode;
import com.shaopc.worthit.reminder.client.command.ReconcileReminderCommand;
import com.shaopc.worthit.reminder.client.model.ReconcileResultCode;
import com.shaopc.worthit.reminder.client.model.ReminderOperationType;
import com.shaopc.worthit.reminder.client.response.ReconcileReminderResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 按 Binding 串行、来源版本和 payload 摘要协调提醒实例。
 */
@Service
public class ReminderReconcileService {

    private static final int MAX_EVENT_ID_LENGTH = 64;
    private final ReminderReconcileRepository repository;
    private final ReminderPayloadDigest payloadDigest;
    private final Clock reminderClock;

    /**
     * 创建 Reminder reconcile 应用服务。
     */
    public ReminderReconcileService(
            ReminderReconcileRepository repository,
            ReminderPayloadDigest payloadDigest,
            Clock reminderClock) {
        this.repository = repository;
        this.payloadDigest = payloadDigest;
        this.reminderClock = reminderClock;
    }

    /**
     * 严格按架构 10.7 的 ①–⑤ 顺序处理内部 reconcile。
     */
    @Transactional
    public ReconcileReminderResponse reconcile(
            String eventId,
            ReconcileReminderCommand command) {
        validateEventId(eventId);
        LocalDateTime now = LocalDateTime.now(reminderClock);
        String digest = payloadDigest.hash(command);

        // ① 确保 Binding 存在，并锁定稳定业务四元组。
        ReminderBindingState binding =
                repository.ensureAndLockBinding(command, now);

        // ② event_id 幂等必须先于来源版本新旧判断。
        Optional<ReminderCommandHistory> eventHistory =
                repository.findCommandByEventId(eventId);
        if (eventHistory.isPresent()) {
            ReminderCommandHistory history = eventHistory.get();
            if (history.bindingId() == binding.id()
                    && sameRequest(history, command, digest)) {
                return replay(history, binding);
            }
            throw contractConflict();
        }

        // ③ 同 Binding + sourceVersion 只允许一个权威摘要。
        Optional<ReminderCommandHistory> versionHistory =
                repository.findCommandByVersion(
                        binding.id(), command.sourceVersion());
        if (versionHistory.isPresent()) {
            ReminderCommandHistory history =
                    versionHistory.get();
            if (history.payloadDigest().equals(digest)) {
                return replay(history, binding);
            }
            repository.recordConflict(
                    history.id(), eventId, digest, now);
            throw contractConflict();
        }

        // ④ 未见过的旧版本只记 IGNORED_OLD，不回退 Binding/Instance。
        if (command.sourceVersion()
                < binding.lastSourceVersion()) {
            repository.insertCommand(
                    binding.id(),
                    eventId,
                    digest,
                    command,
                    ReconcileResultCode.IGNORED_OLD,
                    now);
            return response(
                    ReconcileResultCode.IGNORED_OLD,
                    false,
                    binding.id(),
                    binding.lastSourceVersion());
        }
        if (command.sourceVersion()
                == binding.lastSourceVersion()) {
            throw contractConflict();
        }

        // ⑤ 新版本只协调一次：归档旧 PENDING、创建新期望、推进 Binding、记日志。
        archivePending(binding.id(), eventId, command, now);
        if (shouldCreatePending(command, now)) {
            repository.createPending(
                    binding.id(), eventId, command, now);
        }
        repository.updateBinding(
                binding.id(),
                command.reminderEnabled(),
                command.sourceVersion(),
                now);
        repository.insertCommand(
                binding.id(),
                eventId,
                digest,
                command,
                ReconcileResultCode.APPLIED,
                now);
        return response(
                ReconcileResultCode.APPLIED,
                false,
                binding.id(),
                command.sourceVersion());
    }

    private void archivePending(
            long bindingId,
            String eventId,
            ReconcileReminderCommand command,
            LocalDateTime now) {
        repository.findPending(bindingId)
                .ifPresent(pending -> {
                    Resolution resolution =
                            resolution(command.operationType());
                    boolean due = !pending.remindAt()
                            .isAfter(now);
                    String status = due
                            && resolution.resolving()
                            && !resolution.forceCanceled()
                            ? "PROCESSED"
                            : "CANCELED";
                    repository.resolvePending(
                            pending.id(),
                            status,
                            resolution.reason(),
                            eventId,
                            now);
                });
    }

    private static boolean shouldCreatePending(
            ReconcileReminderCommand command,
            LocalDateTime now) {
        if (!command.reminderEnabled()
                || command.businessDate() == null
                || command.remindAt() == null
                || command.remindAt().isBefore(now)
                || !businessStatusAllowsReminder(command)) {
            return false;
        }
        return switch (command.operationType()) {
            case INITIAL_SYNC,
                    ENABLE_REMINDER,
                    UPDATE_BUSINESS_DATE,
                    ADVANCE_NEXT_RENEWAL_DATE,
                    CORRECT_BUSINESS_DATE,
                    RESUME_SUBSCRIPTION,
                    CONTINUE_CONSIDERING -> true;
            case DISABLE_REMINDER,
                    PAUSE_SUBSCRIPTION,
                    END_SUBSCRIPTION,
                    PURCHASE_WISH,
                    ABANDON_WISH,
                    DISPOSE_ITEM,
                    DELETE_OBJECT -> false;
        };
    }

    private static boolean businessStatusAllowsReminder(
            ReconcileReminderCommand command) {
        return switch (command.businessType()) {
            case ITEM ->
                    "HOLDING".equals(
                            command.businessStatusCode());
            case SUBSCRIPTION ->
                    "ACTIVE".equals(
                            command.businessStatusCode());
            case WISH ->
                    "CONSIDERING".equals(
                            command.businessStatusCode());
        };
    }

    private static Resolution resolution(
            ReminderOperationType operationType) {
        return switch (operationType) {
            case DISABLE_REMINDER ->
                    new Resolution(
                            "REMINDER_DISABLED", false, true);
            case DELETE_OBJECT ->
                    new Resolution(
                            "OBJECT_DELETED", false, true);
            case CORRECT_BUSINESS_DATE ->
                    new Resolution(
                            "DATA_CORRECTION", false, true);
            case UPDATE_BUSINESS_DATE,
                    ADVANCE_NEXT_RENEWAL_DATE ->
                    new Resolution(
                            "BUSINESS_DATE_CHANGED", true, false);
            case PAUSE_SUBSCRIPTION,
                    END_SUBSCRIPTION,
                    PURCHASE_WISH,
                    ABANDON_WISH,
                    CONTINUE_CONSIDERING,
                    DISPOSE_ITEM ->
                    new Resolution(
                            "BUSINESS_ACTION", true, false);
            case RESUME_SUBSCRIPTION ->
                    new Resolution(
                            "BUSINESS_ACTION", false, false);
            case INITIAL_SYNC ->
                    new Resolution(
                            "EXPECTATION_SYNC", false, false);
            case ENABLE_REMINDER ->
                    new Resolution(
                            "REMINDER_ENABLED", false, false);
        };
    }

    private static boolean sameRequest(
            ReminderCommandHistory history,
            ReconcileReminderCommand command,
            String digest) {
        return history.sourceVersion()
                == command.sourceVersion()
                && history.payloadDigest().equals(digest);
    }

    private static ReconcileReminderResponse replay(
            ReminderCommandHistory history,
            ReminderBindingState binding) {
        return response(
                history.resultCode(),
                true,
                binding.id(),
                binding.lastSourceVersion());
    }

    private static ReconcileReminderResponse response(
            ReconcileResultCode resultCode,
            boolean idempotent,
            long bindingId,
            long lastSourceVersion) {
        return new ReconcileReminderResponse(
                resultCode == ReconcileResultCode.APPLIED,
                resultCode,
                idempotent,
                bindingId,
                lastSourceVersion);
    }

    private static void validateEventId(String eventId) {
        if (eventId == null
                || eventId.isBlank()
                || eventId.length() > MAX_EVENT_ID_LENGTH) {
            throw new BusinessException(
                    CommonWebErrorCode.VAL_INVALID_ARGUMENT,
                    "幂等键必须为1至64个字符");
        }
    }

    private static BusinessException contractConflict() {
        return new BusinessException(
                ReminderErrorCode.BIZ_CONTRACT_CONFLICT);
    }

    private record Resolution(
            String reason,
            boolean resolving,
            boolean forceCanceled) {
    }
}
