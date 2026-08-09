package com.shaopc.worthit.auth.accountcancellation.domain;

import java.time.LocalDateTime;

/** 账号注销聚合快照。 */
public record AccountCancellation(
        long id,
        long userId,
        LocalDateTime applyAt,
        LocalDateTime effectiveAt,
        LocalDateTime completedAt,
        AccountCancellationStatus status,
        LocalDateTime revokedAt,
        long version) {
}
