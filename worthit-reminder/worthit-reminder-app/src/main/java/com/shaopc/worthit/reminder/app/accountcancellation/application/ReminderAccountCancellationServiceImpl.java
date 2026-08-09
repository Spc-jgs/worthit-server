package com.shaopc.worthit.reminder.app.accountcancellation.application;

import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.web.error.CommonWebErrorCode;
import com.shaopc.worthit.reminder.app.accountcancellation.domain.ReminderAccountCancellationErrorCode;
import com.shaopc.worthit.reminder.app.accountcancellation.infrastructure.persistence.ReminderAccountCancellationMapper;
import com.shaopc.worthit.reminder.app.accountcancellation.infrastructure.persistence.ReminderUserWriteFenceDO;
import com.shaopc.worthit.reminder.client.response.ReminderAccountCancellationResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

/** 以同一用户围栏行线性化 reconcile、用户写入与物理清理。 */
@Service
public class ReminderAccountCancellationServiceImpl
        implements ReminderAccountCancellationService, ReminderUserWriteFence {

    private static final String ACTIVE = "ACTIVE";
    private static final String CANCELLING = "CANCELLING";
    private static final String CANCELLED = "CANCELLED";

    private final ReminderAccountCancellationMapper mapper;
    private final Clock reminderClock;
    private final Counter cleanup;
    private final Counter replay;
    private final Counter fenceConflict;

    public ReminderAccountCancellationServiceImpl(
            ReminderAccountCancellationMapper mapper,
            Clock reminderClock,
            MeterRegistry meterRegistry) {
        this.mapper = mapper;
        this.reminderClock = reminderClock;
        this.cleanup = counter(meterRegistry, "cleanup");
        this.replay = counter(meterRegistry, "replay");
        this.fenceConflict = counter(meterRegistry, "fence_conflict");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireActive(long userId) {
        LocalDateTime now = now();
        mapper.lockOrCreateActive(userId, now);
        ReminderUserWriteFenceDO fence = requiredFence(userId);
        if (!ACTIVE.equals(fence.getStatus())) {
            throw conflictException();
        }
    }

    @Transactional
    @Override
    public ReminderAccountCancellationResponse cancel(
            long userId, String cancellationId) {
        requireValid(userId, cancellationId);
        LocalDateTime now = now();
        mapper.lockOrCreateActive(userId, now);
        ReminderUserWriteFenceDO fence = requiredFence(userId);
        if (CANCELLED.equals(fence.getStatus())) {
            requireSameCancellation(fence, cancellationId);
            replay.increment();
            return new ReminderAccountCancellationResponse(true);
        }
        if (ACTIVE.equals(fence.getStatus())) {
            if (mapper.beginCancellation(userId, cancellationId, now) != 1) {
                throw new IllegalStateException("Reminder 用户围栏进入注销态失败");
            }
        } else if (CANCELLING.equals(fence.getStatus())) {
            requireSameCancellation(fence, cancellationId);
        } else {
            throw new IllegalStateException("未知 Reminder 用户围栏状态: " + fence.getStatus());
        }

        mapper.deleteCommandLogs(userId);
        mapper.deleteInstances(userId);
        mapper.deleteBindings(userId);
        if (mapper.completeCancellation(userId, cancellationId, now()) != 1) {
            throw new IllegalStateException("Reminder 用户围栏完成注销失败");
        }
        cleanup.increment();
        return new ReminderAccountCancellationResponse(true);
    }

    private ReminderUserWriteFenceDO requiredFence(long userId) {
        return Objects.requireNonNull(
                mapper.selectForUpdate(userId), "Reminder 用户围栏不存在");
    }

    private static void requireValid(long userId, String cancellationId) {
        if (userId <= 0 || !isPositiveLong(cancellationId)) {
            throw new BusinessException(CommonWebErrorCode.VAL_INVALID_ARGUMENT);
        }
    }

    private static boolean isPositiveLong(String value) {
        try {
            return value != null && Long.parseLong(value) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private void requireSameCancellation(
            ReminderUserWriteFenceDO fence, String cancellationId) {
        if (!cancellationId.equals(fence.getCancellationId())) {
            throw conflictException();
        }
    }

    private BusinessException conflictException() {
        fenceConflict.increment();
        return new BusinessException(
                ReminderAccountCancellationErrorCode.VAL_STATE_CONFLICT);
    }

    private static Counter counter(MeterRegistry registry, String result) {
        Objects.requireNonNull(registry, "指标注册器不能为空");
        return Counter.builder("worthit.reminder.account.cancellation")
                .tag("result", result)
                .register(registry);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(reminderClock);
    }
}
