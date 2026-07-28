package com.shaopc.worthit.tracking.subscription.interfaces.rest;

import java.time.LocalDateTime;

/**
 * 删除订阅后的恢复凭据响应。
 */
public record DeleteSubscriptionResponse(
        String id,
        LocalDateTime restoreDeadline,
        String restoreToken) {
}
