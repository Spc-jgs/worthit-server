package com.shaopc.worthit.tracking.item.infrastructure.idempotency;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.tracking.item.application.ItemDetail;
import com.shaopc.worthit.tracking.item.application.ItemIdempotencyClaim;
import com.shaopc.worthit.tracking.item.application.ItemIdempotencyStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 基于 MySQL 唯一键和行锁实现 Item 创建幂等。
 */
@Repository
@RequiredArgsConstructor
public class MybatisItemIdempotencyStore
        implements ItemIdempotencyStore {

    private static final String OPERATION_CODE = "ITEM_CREATE";
    private static final String PROCESSING = "PROCESSING";
    private static final String SUCCEEDED = "SUCCEEDED";
    private final ItemIdempotencyMapper mapper;
    private final ObjectMapper objectMapper;
    private final Clock trackingClock;

    @Override
    public ItemIdempotencyClaim claim(
            long userId,
            String idempotencyKey,
            String requestHash) {
        LocalDateTime now = LocalDateTime.now(trackingClock);
        ItemIdempotencyDO candidate = new ItemIdempotencyDO();
        candidate.setId(IdWorker.getId());
        candidate.setUserId(userId);
        candidate.setOperationCode(OPERATION_CODE);
        candidate.setIdempotencyKey(idempotencyKey);
        candidate.setRequestHash(requestHash);
        candidate.setStatus(PROCESSING);
        candidate.setProcessingExpireAt(now.plusMinutes(1));
        candidate.setExpiresAt(now.plusDays(1));
        candidate.setCreateTime(now);
        candidate.setUpdateTime(now);
        int inserted = mapper.insertClaim(candidate);

        ItemIdempotencyDO locked = mapper.selectForUpdate(
                userId, OPERATION_CODE, idempotencyKey);
        if (!requestHash.equals(locked.getRequestHash())) {
            return new ItemIdempotencyClaim(
                    ItemIdempotencyClaim.Status.CONFLICT,
                    null);
        }
        if (inserted == 1) {
            return new ItemIdempotencyClaim(
                    ItemIdempotencyClaim.Status.NEW,
                    null);
        }
        if (SUCCEEDED.equals(locked.getStatus())
                && locked.getResponseJson() != null) {
            return new ItemIdempotencyClaim(
                    ItemIdempotencyClaim.Status.REPLAY,
                    readResponse(locked.getResponseJson()));
        }
        throw new IllegalStateException(
                "幂等记录状态不完整: " + locked.getStatus());
    }

    @Override
    public void complete(
            long userId,
            String idempotencyKey,
            String requestHash,
            ItemDetail response) {
        try {
            int updated = mapper.complete(
                    userId,
                    OPERATION_CODE,
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

    private ItemDetail readResponse(String responseJson) {
        try {
            return objectMapper.readValue(
                    responseJson, ItemDetail.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Item幂等结果反序列化失败", exception);
        }
    }
}
