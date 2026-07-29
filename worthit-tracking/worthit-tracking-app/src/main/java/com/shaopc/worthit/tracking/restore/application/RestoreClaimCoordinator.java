package com.shaopc.worthit.tracking.restore.application;

import com.shaopc.worthit.tracking.category.application.CategoryReferenceResolver;
import com.shaopc.worthit.tracking.idempotency.application.RestoreTokenClaim;
import com.shaopc.worthit.tracking.idempotency.application.RestoreTokenStore;
import com.shaopc.worthit.tracking.idempotency.application.TrackingOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 在恢复令牌可用前保留业务对象引用的分类。
 */
@Component
@RequiredArgsConstructor
public class RestoreClaimCoordinator {

    private final CategoryReferenceResolver categoryReferenceResolver;
    private final RestoreTokenStore restoreTokenStore;

    /**
     * 先锁定可删除的自定义分类，再锁定并校验恢复令牌。
     *
     * <p>分类保留是令牌首次可用的前置条件，避免恢复在窗口内开始后，
     * 被窗口结束后的分类删除越过。
     *
     * @param userId 用户标识
     * @param categoryId 原分类标识
     * @param operation 恢复操作
     * @param resourceId 资源标识
     * @param deletedVersion 删除后的版本
     * @param restoreToken 恢复令牌
     * @param now 本次恢复判定时刻
     * @param responseType 幂等重放响应类型
     * @param <T> 恢复响应类型
     * @return 恢复令牌校验结果
     */
    public <T> RestoreTokenClaim<T> claimWithCategoryReservation(
            long userId,
            long categoryId,
            TrackingOperation operation,
            long resourceId,
            long deletedVersion,
            String restoreToken,
            LocalDateTime now,
            Class<T> responseType) {
        categoryReferenceResolver.resolve(categoryId, userId);
        return restoreTokenStore.claim(
                userId,
                operation,
                resourceId,
                deletedVersion,
                restoreToken,
                now,
                responseType);
    }
}
