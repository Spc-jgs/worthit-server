package com.shaopc.worthit.tracking.wish.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** 短时恢复想买请求。 */
public record RestoreWishRequest(
        @Positive(message = "版本必须大于0")
        long version,
        @NotBlank(message = "恢复令牌不能为空")
        String restoreToken) {
}
