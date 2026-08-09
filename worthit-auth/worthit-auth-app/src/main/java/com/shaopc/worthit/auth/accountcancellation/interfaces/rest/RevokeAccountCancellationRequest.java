package com.shaopc.worthit.auth.accountcancellation.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** 撤销账号注销请求。 */
public record RevokeAccountCancellationRequest(
        @NotBlank @Schema(type = "string") String cancellationId,
        @Positive @Schema(type = "integer", format = "int64") long version) {
}
