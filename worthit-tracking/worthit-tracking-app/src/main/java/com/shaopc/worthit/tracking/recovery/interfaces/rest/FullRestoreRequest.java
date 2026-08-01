package com.shaopc.worthit.tracking.recovery.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

/**
 * 完整恢复请求。
 */
public record FullRestoreRequest(
        @Positive(message = "版本必须为正整数")
        @Schema(example = "2")
        long version) {
}
