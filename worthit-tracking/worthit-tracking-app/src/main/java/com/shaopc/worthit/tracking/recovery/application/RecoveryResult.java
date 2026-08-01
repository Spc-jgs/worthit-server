package com.shaopc.worthit.tracking.recovery.application;

import com.shaopc.worthit.tracking.recovery.domain.RecoveryResourceType;

/**
 * 完整恢复成功结果。
 */
public record RecoveryResult(
        long id,
        RecoveryResourceType resourceType,
        String name,
        long categoryId,
        String categoryName,
        String status,
        long version,
        boolean categoryFallbackApplied) {
}
