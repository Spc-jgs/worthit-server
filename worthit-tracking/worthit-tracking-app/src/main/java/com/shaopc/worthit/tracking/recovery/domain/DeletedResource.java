package com.shaopc.worthit.tracking.recovery.domain;

import java.time.LocalDateTime;

/**
 * 已逻辑删除 Tracking 资源的只读恢复投影。
 */
public record DeletedResource(
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
