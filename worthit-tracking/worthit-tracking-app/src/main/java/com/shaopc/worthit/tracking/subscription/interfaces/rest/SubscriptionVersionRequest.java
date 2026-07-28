package com.shaopc.worthit.tracking.subscription.interfaces.rest;

import jakarta.validation.constraints.Positive;

/**
 * 订阅状态命令版本请求。
 */
public record SubscriptionVersionRequest(
        @Positive(message = "版本必须大于0")
        long version) {
}
