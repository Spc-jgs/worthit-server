package com.shaopc.worthit.tracking.recovery.interfaces.rest;

import com.shaopc.worthit.tracking.recovery.domain.RecoveryResourceType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 已删除资源列表响应项。
 */
public record DeletedRecoveryResourceResponse(
        @Schema(type = "string", example = "1938")
        String id,
        RecoveryResourceType resourceType,
        String name,
        @Schema(type = "string", example = "100")
        String categoryId,
        String categoryName,
        boolean categoryAvailable,
        String status,
        long version,
        LocalDateTime deletedAt) {
}
