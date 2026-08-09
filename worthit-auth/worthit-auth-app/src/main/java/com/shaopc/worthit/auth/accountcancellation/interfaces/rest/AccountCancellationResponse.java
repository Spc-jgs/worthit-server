package com.shaopc.worthit.auth.accountcancellation.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 账号注销状态响应。 */
public record AccountCancellationResponse(
        @Schema(type = "string") String id,
        String status,
        LocalDateTime applyAt,
        LocalDateTime effectiveAt,
        LocalDateTime revokedAt,
        LocalDateTime completedAt,
        @Schema(type = "integer", format = "int64") long version) {
}
