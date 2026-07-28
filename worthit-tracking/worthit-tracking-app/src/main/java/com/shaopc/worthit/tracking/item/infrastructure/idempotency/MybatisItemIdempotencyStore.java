package com.shaopc.worthit.tracking.item.infrastructure.idempotency;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.tracking.item.application.ItemIdempotencyClaim;
import com.shaopc.worthit.tracking.item.application.ItemIdempotencyStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 基于 MySQL 唯一键和行锁实现 Item 写接口幂等。
 */
@Repository
@RequiredArgsConstructor
public class MybatisItemIdempotencyStore
        implements ItemIdempotencyStore {

    private static final String PROCESSING = "PROCESSING";
    private static final String SUCCEEDED = "SUCCEEDED";
    private final ItemIdempotencyMapper mapper;
    private final ObjectMapper objectMapper;
    private final Clock trackingClock;

    @Override
    public <T> ItemIdempotencyClaim<T> claim(
            long userId,
            String operationCode,
            String idempotencyKey,
            String requestHash,
            Class<T> responseType) {
        LocalDateTime now = LocalDateTime.now(trackingClock);
        ItemIdempotencyDO candidate = new ItemIdempotencyDO();
        candidate.setId(IdWorker.getId());
        candidate.setUserId(userId);
        candidate.setOperationCode(operationCode);
        candidate.setIdempotencyKey(idempotencyKey);
        candidate.setRequestHash(requestHash);
        candidate.setStatus(PROCESSING);
        candidate.setProcessingExpireAt(now.plusMinutes(1));
        candidate.setExpiresAt(now.plusDays(1));
        candidate.setCreateTime(now);
        candidate.setUpdateTime(now);
        int inserted = mapper.insertClaim(candidate);

        ItemIdempotencyDO locked = mapper.selectForUpdate(
                userId, operationCode, idempotencyKey);
        if (!requestHash.equals(locked.getRequestHash())) {
            return new ItemIdempotencyClaim<>(
                    ItemIdempotencyClaim.Status.CONFLICT,
                    null);
        }
        if (inserted == 1) {
            return new ItemIdempotencyClaim<>(
                    ItemIdempotencyClaim.Status.NEW,
                    null);
        }
        if (SUCCEEDED.equals(locked.getStatus())
                && locked.getResponseJson() != null) {
            return new ItemIdempotencyClaim<>(
                    ItemIdempotencyClaim.Status.REPLAY,
                    readResponse(
                            locked.getResponseJson(),
                            responseType));
        }
        throw new IllegalStateException(
                "幂等记录状态不完整: " + locked.getStatus());
    }

    @Override
    public <T> void complete(
            long userId,
            String operationCode,
            String idempotencyKey,
            String requestHash,
            T response) {
        try {
            int updated = mapper.complete(
                    userId,
                    operationCode,
                    idempotencyKey,
                    requestHash,
                    objectMapper.writeValueAsString(response),
                    LocalDateTime.now(trackingClock));
            if (updated != 1) {
                throw new IllegalStateException(
                        "Item幂等结果写入失败");
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Item幂等结果序列化失败", exception);
        }
    }

    private <T> T readResponse(
            String responseJson, Class<T> responseType) {
        try {
            return objectMapper.readValue(
                    responseJson, responseType);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Item幂等结果反序列化失败", exception);
        }
    }
}
