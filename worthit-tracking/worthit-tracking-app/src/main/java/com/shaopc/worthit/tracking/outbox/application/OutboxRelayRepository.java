package com.shaopc.worthit.tracking.outbox.application;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox Relay 租约抢占和结果回写边界。
 */
public interface OutboxRelayRepository {

    /**
     * 抢占一批到期事件，并回收超过租约的 PROCESSING 事件。
     */
    List<ClaimedOutboxEvent> claim(
            String ownerId,
            LocalDateTime now,
            LocalDateTime leaseExpiredAt,
            int limit);

    /**
     * 当前租约仍有效时写入成功终态。
     */
    boolean markSucceeded(
            long eventId,
            String ownerId,
            LocalDateTime now);

    /**
     * 当前租约仍有效时写入重试或死信状态。
     */
    boolean markFailed(
            long eventId,
            String ownerId,
            String status,
            int retryCount,
            LocalDateTime nextRetryAt,
            String lastError,
            LocalDateTime processedAt,
            LocalDateTime now);
}
