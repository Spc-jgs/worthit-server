package com.shaopc.worthit.tracking.outbox.infrastructure.persistence;

import com.shaopc.worthit.tracking.outbox.application.ClaimedOutboxEvent;
import com.shaopc.worthit.tracking.outbox.application.OutboxRelayRepository;
import com.shaopc.worthit.tracking.outbox.application.OutboxStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 MySQL 行锁和租约字段实现 Outbox Relay。
 */
@Repository
public class MybatisOutboxRelayRepository
        implements OutboxRelayRepository {

    private final OutboxEventMapper mapper;

    /**
     * 创建 MyBatis Outbox Relay 仓储。
     */
    public MybatisOutboxRelayRepository(
            OutboxEventMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public List<ClaimedOutboxEvent> claim(
            String ownerId,
            LocalDateTime now,
            LocalDateTime leaseExpiredAt,
            int limit) {
        List<OutboxEventDO> candidates =
                mapper.selectClaimableForUpdate(
                        now,
                        leaseExpiredAt,
                        OutboxStatus.NEW.code(),
                        OutboxStatus.RETRY_WAIT.code(),
                        OutboxStatus.PROCESSING.code(),
                        limit);
        List<ClaimedOutboxEvent> claimed =
                new ArrayList<>(candidates.size());
        for (OutboxEventDO candidate : candidates) {
            if (mapper.claim(
                    candidate.getId(),
                    ownerId,
                    now,
                    OutboxStatus.PROCESSING.code(),
                    OutboxStatus.NEW.code(),
                    OutboxStatus.RETRY_WAIT.code(),
                    OutboxStatus.PROCESSING.code(),
                    leaseExpiredAt) == 1) {
                claimed.add(toClaimed(candidate));
            }
        }
        return claimed;
    }

    @Override
    @Transactional
    public boolean markSucceeded(
            long eventId,
            String ownerId,
            LocalDateTime now) {
        return mapper.markSucceeded(
                eventId,
                ownerId,
                OutboxStatus.SUCCEEDED.code(),
                OutboxStatus.PROCESSING.code(),
                now) == 1;
    }

    @Override
    @Transactional
    public boolean markFailed(
            long eventId,
            String ownerId,
            OutboxStatus status,
            int retryCount,
            LocalDateTime nextRetryAt,
            String lastError,
            LocalDateTime processedAt,
            LocalDateTime now) {
        return mapper.markFailed(
                eventId,
                ownerId,
                status.code(),
                OutboxStatus.PROCESSING.code(),
                retryCount,
                nextRetryAt,
                lastError,
                processedAt,
                now) == 1;
    }

    private static ClaimedOutboxEvent toClaimed(
            OutboxEventDO event) {
        return new ClaimedOutboxEvent(
                event.getId(),
                event.getEventId(),
                event.getEventType(),
                event.getPayloadJson(),
                event.getSchemaVersion(),
                event.getRetryCount());
    }
}
