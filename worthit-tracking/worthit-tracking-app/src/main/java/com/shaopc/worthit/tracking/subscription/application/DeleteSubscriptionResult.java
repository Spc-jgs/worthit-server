package com.shaopc.worthit.tracking.subscription.application;

import java.time.LocalDateTime;

/**
 * 删除订阅后的短时恢复凭据。
 */
public record DeleteSubscriptionResult(
        long id,
        LocalDateTime restoreDeadline,
        String restoreToken) {
}
