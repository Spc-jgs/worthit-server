package com.shaopc.worthit.tracking.subscription.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * 短时恢复订阅请求。
 */
public record RestoreSubscriptionRequest(
        @Positive(message = "版本必须大于0")
        long version,
        @NotBlank(message = "恢复令牌不能为空")
        @Pattern(
                regexp = "[0-9a-fA-F-]{36}",
                message = "恢复令牌格式不正确")
        String restoreToken) {
}
