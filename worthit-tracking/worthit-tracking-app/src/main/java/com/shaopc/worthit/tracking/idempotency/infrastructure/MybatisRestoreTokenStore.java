package com.shaopc.worthit.tracking.idempotency.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.tracking.idempotency.application.RestoreTokenClaim;
import com.shaopc.worthit.tracking.idempotency.application.RestoreTokenStore;
import com.shaopc.worthit.tracking.idempotency.application.TrackingOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 复用 Tracking 幂等表保存短时恢复令牌和重放结果。
 */
@Repository
@RequiredArgsConstructor
public class MybatisRestoreTokenStore
        implements RestoreTokenStore {

    private final IdempotencyMapper mapper;
    private final ObjectMapper objectMapper;
    private final Clock trackingClock;

    @Override
    public String issue(
            long userId,
            TrackingOperation operation,
            long resourceId,
            long deletedVersion,
            LocalDateTime deadline) {
        LocalDateTime now = LocalDateTime.now(trackingClock);
        String restoreToken = UUID.randomUUID().toString();
        IdempotencyDO grant = new IdempotencyDO();
        grant.setId(IdWorker.getId());
        grant.setUserId(userId);
        grant.setOperationCode(operation.code());
        grant.setIdempotencyKey(restoreToken);
        grant.setRequestHash(hash(resourceId, deletedVersion));
        grant.setStatus(
                IdempotencyRecordStatus.PROCESSING.code());
        grant.setProcessingExpireAt(deadline);
        grant.setExpiresAt(deadline);
        grant.setCreateTime(now);
        grant.setUpdateTime(now);
        if (mapper.insertClaim(grant) != 1) {
            throw new IllegalStateException(
                    "恢复令牌创建失败");
        }
        return restoreToken;
    }

    @Override
    public <T> RestoreTokenClaim<T> claim(
            long userId,
            TrackingOperation operation,
            long resourceId,
            long deletedVersion,
            String restoreToken,
            LocalDateTime now,
            Class<T> responseType) {
        IdempotencyDO grant = mapper.selectForUpdate(
                userId, operation.code(), restoreToken);
        if (grant == null) {
            return claim(RestoreTokenClaim.Status.EXPIRED);
        }
        if (!hash(resourceId, deletedVersion)
                .equals(grant.getRequestHash())) {
            return claim(RestoreTokenClaim.Status.CONFLICT);
        }
        IdempotencyRecordStatus status =
                IdempotencyRecordStatus.fromCode(
                        grant.getStatus());
        if (status == IdempotencyRecordStatus.SUCCEEDED
                && grant.getResponseJson() != null) {
            return new RestoreTokenClaim<>(
                    RestoreTokenClaim.Status.REPLAY,
                    readResponse(
                            grant.getResponseJson(),
                            responseType));
        }
        if (status != IdempotencyRecordStatus.PROCESSING
                || now.isAfter(grant.getExpiresAt())) {
            return claim(RestoreTokenClaim.Status.EXPIRED);
        }
        return claim(RestoreTokenClaim.Status.AVAILABLE);
    }

    @Override
    public <T> void complete(
            long userId,
            TrackingOperation operation,
            long resourceId,
            long deletedVersion,
            String restoreToken,
            T response) {
        try {
            int updated = mapper.complete(
                    userId,
                    operation.code(),
                    restoreToken,
                    hash(resourceId, deletedVersion),
                    objectMapper.writeValueAsString(response),
                    IdempotencyRecordStatus.SUCCEEDED.code(),
                    IdempotencyRecordStatus.PROCESSING.code(),
                    LocalDateTime.now(trackingClock));
            if (updated != 1) {
                throw new IllegalStateException(
                        "恢复幂等结果写入失败");
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "恢复结果序列化失败", exception);
        }
    }

    private <T> T readResponse(
            String responseJson, Class<T> responseType) {
        try {
            return objectMapper.readValue(
                    responseJson, responseType);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "恢复结果反序列化失败", exception);
        }
    }

    private static <T> RestoreTokenClaim<T> claim(
            RestoreTokenClaim.Status status) {
        return new RestoreTokenClaim<>(status, null);
    }

    private static String hash(
            long resourceId, long deletedVersion) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance(
                            DigestAlgorithms.SHA_256);
            byte[] bytes = digest.digest(
                    (resourceId + ":" + deletedVersion)
                            .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "当前JDK不支持SHA-256", exception);
        }
    }
}
