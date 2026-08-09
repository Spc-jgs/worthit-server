package com.shaopc.worthit.tracking.accountcancellation.application;

import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.web.error.CommonWebErrorCode;
import com.shaopc.worthit.tracking.accountcancellation.domain.TrackingAccountCancellationErrorCode;
import com.shaopc.worthit.tracking.accountcancellation.infrastructure.persistence.TrackingAccountCancellationMapper;
import com.shaopc.worthit.tracking.accountcancellation.infrastructure.persistence.TrackingUserWriteFenceDO;
import com.shaopc.worthit.tracking.client.response.TrackingAccountCancellationResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

/** 以同一用户围栏行线性化业务写入与物理清理。 */
@Service
public class TrackingAccountCancellationServiceImpl
        implements TrackingAccountCancellationService, TrackingUserWriteFence {

    private static final String ACTIVE = "ACTIVE";
    private static final String CANCELLING = "CANCELLING";
    private static final String CANCELLED = "CANCELLED";

    private final TrackingAccountCancellationMapper mapper;
    private final Clock trackingClock;
    private final Counter cleanup;
    private final Counter replay;
    private final Counter fenceConflict;

    public TrackingAccountCancellationServiceImpl(
            TrackingAccountCancellationMapper mapper,
            Clock trackingClock,
            MeterRegistry meterRegistry) {
        this.mapper = mapper;
        this.trackingClock = trackingClock;
        this.cleanup = counter(meterRegistry, "cleanup");
        this.replay = counter(meterRegistry, "replay");
        this.fenceConflict = counter(meterRegistry, "fence_conflict");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireActive(long userId) {
        LocalDateTime now = now();
        mapper.lockOrCreateActive(userId, now);
        TrackingUserWriteFenceDO fence = requiredFence(userId);
        if (!ACTIVE.equals(fence.getStatus())) {
            throw conflictException();
        }
    }

    @Transactional
    @Override
    public TrackingAccountCancellationResponse cancel(
            long userId, String cancellationId) {
        requireValid(userId, cancellationId);
        LocalDateTime now = now();
        mapper.lockOrCreateActive(userId, now);
        TrackingUserWriteFenceDO fence = requiredFence(userId);
        if (CANCELLED.equals(fence.getStatus())) {
            requireSameCancellation(fence, cancellationId);
            replay.increment();
            return new TrackingAccountCancellationResponse(true);
        }
        if (ACTIVE.equals(fence.getStatus())) {
            if (mapper.beginCancellation(userId, cancellationId, now) != 1) {
                throw new IllegalStateException("Tracking 用户围栏进入注销态失败");
            }
        } else if (CANCELLING.equals(fence.getStatus())) {
            requireSameCancellation(fence, cancellationId);
        } else {
            throw new IllegalStateException("未知 Tracking 用户围栏状态: " + fence.getStatus());
        }

        mapper.deleteReplacements(userId);
        mapper.deleteDisposals(userId);
        mapper.deleteOutbox(userId);
        mapper.deleteIdempotency(userId);
        mapper.deleteWishes(userId);
        mapper.deleteSubscriptions(userId);
        mapper.deleteItems(userId);
        mapper.deleteCategories(userId);
        if (mapper.completeCancellation(userId, cancellationId, now()) != 1) {
            throw new IllegalStateException("Tracking 用户围栏完成注销失败");
        }
        cleanup.increment();
        return new TrackingAccountCancellationResponse(true);
    }

    private TrackingUserWriteFenceDO requiredFence(long userId) {
        return Objects.requireNonNull(
                mapper.selectForUpdate(userId), "Tracking 用户围栏不存在");
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
            TrackingUserWriteFenceDO fence, String cancellationId) {
        if (!cancellationId.equals(fence.getCancellationId())) {
            throw conflictException();
        }
    }

    private BusinessException conflictException() {
        fenceConflict.increment();
        return new BusinessException(
                TrackingAccountCancellationErrorCode.VAL_STATE_CONFLICT);
    }

    private static Counter counter(MeterRegistry registry, String result) {
        Objects.requireNonNull(registry, "指标注册器不能为空");
        return Counter.builder("worthit.tracking.account.cancellation")
                .tag("result", result)
                .register(registry);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(trackingClock);
    }
}
