package com.shaopc.worthit.tracking.recovery.interfaces.rest;

import com.shaopc.worthit.tracking.recovery.domain.RecoveryResourceType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 完整恢复成功响应。
 */
public record FullRestoreResponse(
        @Schema(type = "string", example = "1938")
        String id,
        RecoveryResourceType resourceType,
        String name,
        @Schema(type = "string", example = "100")
        String categoryId,
        String categoryName,
        String status,
        long version,
        boolean categoryFallbackApplied) {
}
