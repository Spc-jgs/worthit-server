package com.shaopc.worthit.tracking.recovery.application;

import com.shaopc.worthit.tracking.recovery.domain.RecoveryResourceType;

import java.time.LocalDateTime;

/**
 * 已删除资源列表项。
 */
public record RecoveryResourceSummary(
        long id,
        RecoveryResourceType resourceType,
        String name,
        long categoryId,
        String categoryName,
        boolean categoryAvailable,
        String status,
        long version,
        LocalDateTime deletedAt) {
}
