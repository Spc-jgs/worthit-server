package com.shaopc.worthit.tracking.item.application;

import java.time.LocalDateTime;

/**
 * Item 短时恢复令牌持久化边界。
 */
public interface ItemRestoreTokenStore {

    /**
     * 为一次成功删除创建恢复令牌。
     *
     * @return 服务端生成的恢复令牌
     */
    String issue(
            long userId,
            long itemId,
            long deletedVersion,
            LocalDateTime deadline);

    /**
     * 锁定并校验恢复令牌。
     */
    ItemRestoreTokenClaim claim(
            long userId,
            long itemId,
            long deletedVersion,
            String restoreToken,
            LocalDateTime now);

    /**
     * 保存首次恢复结果，供重复请求幂等重放。
     */
    void complete(
            long userId,
            long itemId,
            long deletedVersion,
            String restoreToken,
            ItemDetail response);
}
