package com.shaopc.worthit.auth.accountcancellation.infrastructure.idempotency;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.auth.accountcancellation.application.idempotency.AuthCancellationOperation;
import com.shaopc.worthit.auth.accountcancellation.application.idempotency.AuthIdempotencyClaim;
import com.shaopc.worthit.auth.accountcancellation.application.idempotency.AuthIdempotencyStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** 基于 MySQL 唯一键、行锁和租约 fencing 的 Auth 幂等存储。 */
@Repository
@RequiredArgsConstructor
public class MybatisAuthIdempotencyStore implements AuthIdempotencyStore {

    private static final Duration PROCESSING_TIMEOUT = Duration.ofMinutes(1);
    private static final Duration RECORD_RETENTION = Duration.ofDays(90);
    private final AuthIdempotencyMapper mapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public <T> AuthIdempotencyClaim<T> claim(
            long userId,
            AuthCancellationOperation operation,
            String idempotencyKey,
            String requestHash,
            Class<T> responseType) {
        LocalDateTime now = now();
        LocalDateTime newLease = now.plus(PROCESSING_TIMEOUT);
        AuthIdempotencyDO candidate = candidate(
                userId, operation, idempotencyKey, requestHash, now, newLease);
        mapper.insertClaim(candidate);
        AuthIdempotencyDO locked = mapper.selectForUpdate(
                userId, operation.code(), idempotencyKey);
        if (locked == null) {
            throw new IllegalStateException("Auth 幂等占位记录不存在");
        }
        if (!requestHash.equals(locked.getRequestHash())) {
            return claim(AuthIdempotencyClaim.Status.CONFLICT);
        }
        if (candidate.getId().equals(locked.getId())) {
            return new AuthIdempotencyClaim<>(
                    AuthIdempotencyClaim.Status.NEW,
                    null,
                    null,
                    null,
                    newLease);
        }
        return switch (locked.getStatus()) {
            case "SUCCEEDED" -> replaySuccess(locked, responseType);
            case "FAILED" -> replayFailure(locked);
            case "PROCESSING" -> processing(
                    locked,
                    userId,
                    operation,
                    idempotencyKey,
                    requestHash,
                    now,
                    newLease);
            default -> throw new IllegalStateException(
                    "未知 Auth 幂等状态: " + locked.getStatus());
        };
    }

    @Override
    public <T> void completeSuccess(
            long userId,
            AuthCancellationOperation operation,
            String idempotencyKey,
            String requestHash,
            LocalDateTime leaseExpiresAt,
            T response) {
        try {
            int updated = mapper.completeSuccess(
                    userId,
                    operation.code(),
                    idempotencyKey,
                    requestHash,
                    leaseExpiresAt,
                    objectMapper.writeValueAsString(response),
                    now());
            requireCompleted(updated);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Auth 幂等响应序列化失败", exception);
        }
    }

    @Override
    public void completeFailure(
            long userId,
            AuthCancellationOperation operation,
            String idempotencyKey,
            String requestHash,
            LocalDateTime leaseExpiresAt,
            String errorCode,
            String errorMessage) {
        int updated = mapper.completeFailure(
                userId,
                operation.code(),
                idempotencyKey,
                requestHash,
                leaseExpiresAt,
                bounded(errorCode, 64),
                bounded(errorMessage, 256),
                now());
        requireCompleted(updated);
    }

    private <T> AuthIdempotencyClaim<T> processing(
            AuthIdempotencyDO locked,
            long userId,
            AuthCancellationOperation operation,
            String idempotencyKey,
            String requestHash,
            LocalDateTime now,
            LocalDateTime newLease) {
        LocalDateTime oldLease = locked.getProcessingExpireAt();
        if (oldLease == null) {
            throw new IllegalStateException("Auth PROCESSING 幂等记录缺少租约");
        }
        if (oldLease.isAfter(now)) {
            return new AuthIdempotencyClaim<>(
                    AuthIdempotencyClaim.Status.IN_PROGRESS,
                    null,
                    null,
                    null,
                    oldLease);
        }
        if (mapper.reclaim(
                userId,
                operation.code(),
                idempotencyKey,
                requestHash,
                oldLease,
                newLease,
                now.plus(RECORD_RETENTION),
                now) != 1) {
            throw new IllegalStateException("Auth 过期幂等租约接管失败");
        }
        return new AuthIdempotencyClaim<>(
                AuthIdempotencyClaim.Status.NEW,
                null,
                null,
                null,
                newLease);
    }

    private <T> AuthIdempotencyClaim<T> replaySuccess(
            AuthIdempotencyDO value, Class<T> responseType) {
        try {
            return new AuthIdempotencyClaim<>(
                    AuthIdempotencyClaim.Status.REPLAY_SUCCESS,
                    objectMapper.readValue(value.getResponseJson(), responseType),
                    null,
                    null,
                    null);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Auth 幂等响应反序列化失败", exception);
        }
    }

    private static <T> AuthIdempotencyClaim<T> replayFailure(
            AuthIdempotencyDO value) {
        return new AuthIdempotencyClaim<>(
                AuthIdempotencyClaim.Status.REPLAY_FAILURE,
                null,
                value.getErrorCode(),
                value.getErrorMessage(),
                null);
    }

    private static <T> AuthIdempotencyClaim<T> claim(
            AuthIdempotencyClaim.Status status) {
        return new AuthIdempotencyClaim<>(status, null, null, null, null);
    }

    private static AuthIdempotencyDO candidate(
            long userId,
            AuthCancellationOperation operation,
            String idempotencyKey,
            String requestHash,
            LocalDateTime now,
            LocalDateTime leaseExpiresAt) {
        AuthIdempotencyDO value = new AuthIdempotencyDO();
        value.setId(IdWorker.getId());
        value.setUserId(userId);
        value.setOperationCode(operation.code());
        value.setIdempotencyKey(idempotencyKey);
        value.setRequestHash(requestHash);
        value.setStatus("PROCESSING");
        value.setProcessingExpireAt(leaseExpiresAt);
        value.setExpiresAt(now.plus(RECORD_RETENTION));
        value.setCreateTime(now);
        value.setUpdateTime(now);
        return value;
    }

    private static String bounded(String value, int maxLength) {
        String safe = value == null || value.isBlank() ? "未知错误" : value;
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }

    private static void requireCompleted(int updated) {
        if (updated != 1) {
            throw new IllegalStateException("Auth 幂等租约已失效");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
    }
}
