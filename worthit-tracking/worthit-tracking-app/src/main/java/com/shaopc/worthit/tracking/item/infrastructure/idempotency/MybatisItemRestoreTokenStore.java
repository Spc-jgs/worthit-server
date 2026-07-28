package com.shaopc.worthit.tracking.item.infrastructure.idempotency;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.tracking.item.application.ItemDetail;
import com.shaopc.worthit.tracking.item.application.ItemRestoreTokenClaim;
import com.shaopc.worthit.tracking.item.application.ItemRestoreTokenStore;
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
 * 复用 Tracking 幂等表保存 Item 短时恢复令牌和重放结果。
 */
@Repository
@RequiredArgsConstructor
public class MybatisItemRestoreTokenStore
        implements ItemRestoreTokenStore {

    private static final String OPERATION_CODE = "ITEM_RESTORE";
    private static final String PROCESSING = "PROCESSING";
    private static final String SUCCEEDED = "SUCCEEDED";
    private final ItemIdempotencyMapper mapper;
    private final ObjectMapper objectMapper;
    private final Clock trackingClock;

    @Override
    public String issue(
            long userId,
            long itemId,
            long deletedVersion,
            LocalDateTime deadline) {
        LocalDateTime now = LocalDateTime.now(trackingClock);
        String restoreToken = UUID.randomUUID().toString();
        ItemIdempotencyDO grant = new ItemIdempotencyDO();
        grant.setId(IdWorker.getId());
        grant.setUserId(userId);
        grant.setOperationCode(OPERATION_CODE);
        grant.setIdempotencyKey(restoreToken);
        grant.setRequestHash(hash(itemId, deletedVersion));
        grant.setStatus(PROCESSING);
        grant.setProcessingExpireAt(deadline);
        grant.setExpiresAt(deadline);
        grant.setCreateTime(now);
        grant.setUpdateTime(now);
        if (mapper.insertClaim(grant) != 1) {
            throw new IllegalStateException("Item恢复令牌创建失败");
        }
        return restoreToken;
    }

    @Override
    public ItemRestoreTokenClaim claim(
            long userId,
            long itemId,
            long deletedVersion,
            String restoreToken,
            LocalDateTime now) {
        ItemIdempotencyDO grant = mapper.selectForUpdate(
                userId, OPERATION_CODE, restoreToken);
        if (grant == null) {
            return claim(ItemRestoreTokenClaim.Status.EXPIRED);
        }
        if (!hash(itemId, deletedVersion)
                .equals(grant.getRequestHash())) {
            return claim(ItemRestoreTokenClaim.Status.CONFLICT);
        }
        if (SUCCEEDED.equals(grant.getStatus())
                && grant.getResponseJson() != null) {
            return new ItemRestoreTokenClaim(
                    ItemRestoreTokenClaim.Status.REPLAY,
                    readResponse(grant.getResponseJson()));
        }
        if (!PROCESSING.equals(grant.getStatus())
                || now.isAfter(grant.getExpiresAt())) {
            return claim(ItemRestoreTokenClaim.Status.EXPIRED);
        }
        return claim(ItemRestoreTokenClaim.Status.AVAILABLE);
    }

    @Override
    public void complete(
            long userId,
            long itemId,
            long deletedVersion,
            String restoreToken,
            ItemDetail response) {
        try {
            int updated = mapper.complete(
                    userId,
                    OPERATION_CODE,
                    restoreToken,
                    hash(itemId, deletedVersion),
                    objectMapper.writeValueAsString(response),
                    LocalDateTime.now(trackingClock));
            if (updated != 1) {
                throw new IllegalStateException(
                        "Item恢复幂等结果写入失败");
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Item恢复结果序列化失败", exception);
        }
    }

    private ItemDetail readResponse(String responseJson) {
        try {
            return objectMapper.readValue(
                    responseJson, ItemDetail.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Item恢复结果反序列化失败", exception);
        }
    }

    private static ItemRestoreTokenClaim claim(
            ItemRestoreTokenClaim.Status status) {
        return new ItemRestoreTokenClaim(status, null);
    }

    private static String hash(
            long itemId, long deletedVersion) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(
                    (itemId + ":" + deletedVersion)
                            .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "当前JDK不支持SHA-256", exception);
        }
    }
}
