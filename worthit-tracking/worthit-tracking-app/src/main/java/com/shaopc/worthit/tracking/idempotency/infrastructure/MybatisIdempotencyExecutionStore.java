package com.shaopc.worthit.tracking.idempotency.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.tracking.idempotency.application.IdempotencyExecutionClaim;
import com.shaopc.worthit.tracking.idempotency.application.IdempotencyExecutionStore;
import com.shaopc.worthit.tracking.idempotency.application.TrackingOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 基于 MySQL 唯一键、行锁和租约 fencing 的幂等执行存储。
 */
@Repository
@RequiredArgsConstructor
public class MybatisIdempotencyExecutionStore
        implements IdempotencyExecutionStore {

    private static final Duration PROCESSING_TIMEOUT =
            Duration.ofMinutes(1);
    private static final Duration RECORD_RETENTION =
            Duration.ofDays(1);
    private static final int MAX_ERROR_CODE_LENGTH = 64;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 256;

    private final IdempotencyMapper mapper;
    private final ObjectMapper objectMapper;
    private final Clock trackingClock;

    @Override
    public <T> IdempotencyExecutionClaim<T> claim(
            long userId,
            TrackingOperation operation,
            String idempotencyKey,
            String requestHash,
            Class<T> responseType) {
        LocalDateTime now = databaseTime();
        LocalDateTime newLease =
                now.plus(PROCESSING_TIMEOUT);
        IdempotencyDO candidate = candidate(
                userId,
                operation,
                idempotencyKey,
                requestHash,
                now,
                newLease);
        int inserted = mapper.insertClaim(candidate);
        IdempotencyDO locked = mapper.selectForUpdate(
                userId, operation.code(), idempotencyKey);
        if (locked == null) {
            throw new IllegalStateException(
                    "幂等占位写入后记录不存在");
        }
        if (!requestHash.equals(locked.getRequestHash())) {
            return claim(
                    IdempotencyExecutionClaim.Status.CONFLICT);
        }
        if (inserted == 1) {
            return new IdempotencyExecutionClaim<>(
                    IdempotencyExecutionClaim.Status.NEW,
                    null,
                    null,
                    null,
                    newLease);
        }

        IdempotencyRecordStatus status =
                IdempotencyRecordStatus.fromCode(
                        locked.getStatus());
        return switch (status) {
            case SUCCEEDED -> replaySuccess(
                    locked, responseType);
            case FAILED -> replayFailure(locked);
            case PROCESSING -> processingClaim(
                    locked,
                    userId,
                    operation,
                    idempotencyKey,
                    requestHash,
                    now,
                    newLease);
        };
    }

    @Override
    public <T> void completeSuccess(
            long userId,
            TrackingOperation operation,
            String idempotencyKey,
            String requestHash,
            LocalDateTime leaseExpiresAt,
            T response) {
        String responseJson;
        try {
            responseJson = objectMapper.writeValueAsString(
                    response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Tracking幂等结果序列化失败", exception);
        }
        int updated = mapper.completeExecutionSuccess(
                userId,
                operation.code(),
                idempotencyKey,
                requestHash,
                leaseExpiresAt,
                responseJson,
                IdempotencyRecordStatus.SUCCEEDED.code(),
                IdempotencyRecordStatus.PROCESSING.code(),
                databaseTime());
        requireSingleCompletion(updated);
    }

    @Override
    public void completeFailure(
            long userId,
            TrackingOperation operation,
            String idempotencyKey,
            String requestHash,
            LocalDateTime leaseExpiresAt,
            String errorCode,
            String errorMessage) {
        String safeCode = requireBounded(
                errorCode,
                MAX_ERROR_CODE_LENGTH,
                "幂等失败错误码");
        String safeMessage = boundedMessage(errorMessage);
        int updated = mapper.completeExecutionFailure(
                userId,
                operation.code(),
                idempotencyKey,
                requestHash,
                leaseExpiresAt,
                safeCode,
                safeMessage,
                IdempotencyRecordStatus.FAILED.code(),
                IdempotencyRecordStatus.PROCESSING.code(),
                databaseTime());
        requireSingleCompletion(updated);
    }

    private <T> IdempotencyExecutionClaim<T> processingClaim(
            IdempotencyDO locked,
            long userId,
            TrackingOperation operation,
            String idempotencyKey,
            String requestHash,
            LocalDateTime now,
            LocalDateTime newLease) {
        LocalDateTime currentLease =
                locked.getProcessingExpireAt();
        if (currentLease == null) {
            throw new IllegalStateException(
                    "PROCESSING幂等记录缺少租约");
        }
        if (currentLease.isAfter(now)) {
            return new IdempotencyExecutionClaim<>(
                    IdempotencyExecutionClaim.Status.IN_PROGRESS,
                    null,
                    null,
                    null,
                    currentLease);
        }
        int reclaimed = mapper.reclaimExecution(
                userId,
                operation.code(),
                idempotencyKey,
                requestHash,
                currentLease,
                newLease,
                now.plus(RECORD_RETENTION),
                IdempotencyRecordStatus.PROCESSING.code(),
                now);
        if (reclaimed != 1) {
            throw new IllegalStateException(
                    "过期幂等租约接管失败");
        }
        return new IdempotencyExecutionClaim<>(
                IdempotencyExecutionClaim.Status.NEW,
                null,
                null,
                null,
                newLease);
    }

    private <T> IdempotencyExecutionClaim<T> replaySuccess(
            IdempotencyDO locked,
            Class<T> responseType) {
        if (locked.getResponseJson() == null) {
            throw new IllegalStateException(
                    "SUCCEEDED幂等记录缺少响应");
        }
        try {
            return new IdempotencyExecutionClaim<>(
                    IdempotencyExecutionClaim.Status.REPLAY_SUCCESS,
                    objectMapper.readValue(
                            locked.getResponseJson(),
                            responseType),
                    null,
                    null,
                    null);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Tracking幂等结果反序列化失败", exception);
        }
    }

    private static <T> IdempotencyExecutionClaim<T>
            replayFailure(IdempotencyDO locked) {
        if (locked.getErrorCode() == null
                || locked.getErrorMessage() == null) {
            throw new IllegalStateException(
                    "FAILED幂等记录缺少错误结果");
        }
        return new IdempotencyExecutionClaim<>(
                IdempotencyExecutionClaim.Status.REPLAY_FAILURE,
                null,
                locked.getErrorCode(),
                locked.getErrorMessage(),
                null);
    }

    private static <T> IdempotencyExecutionClaim<T> claim(
            IdempotencyExecutionClaim.Status status) {
        return new IdempotencyExecutionClaim<>(
                status, null, null, null, null);
    }

    private static IdempotencyDO candidate(
            long userId,
            TrackingOperation operation,
            String idempotencyKey,
            String requestHash,
            LocalDateTime now,
            LocalDateTime leaseExpiresAt) {
        IdempotencyDO candidate = new IdempotencyDO();
        candidate.setId(IdWorker.getId());
        candidate.setUserId(userId);
        candidate.setOperationCode(operation.code());
        candidate.setIdempotencyKey(idempotencyKey);
        candidate.setRequestHash(requestHash);
        candidate.setStatus(
                IdempotencyRecordStatus.PROCESSING.code());
        candidate.setProcessingExpireAt(leaseExpiresAt);
        candidate.setExpiresAt(
                now.plus(RECORD_RETENTION));
        candidate.setCreateTime(now);
        candidate.setUpdateTime(now);
        return candidate;
    }

    private static void requireSingleCompletion(int updated) {
        if (updated != 1) {
            throw new IllegalStateException(
                    "幂等租约已失效，拒绝提交业务结果");
        }
    }

    private LocalDateTime databaseTime() {
        return LocalDateTime.now(trackingClock)
                .truncatedTo(ChronoUnit.MILLIS);
    }

    private static String boundedMessage(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "幂等失败消息不能为空");
        }
        return value.length() <= MAX_ERROR_MESSAGE_LENGTH
                ? value
                : value.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    private static String requireBounded(
            String value, int maxLength, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    name + "不能为空");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(
                    name + "长度超过" + maxLength);
        }
        return value;
    }
}
