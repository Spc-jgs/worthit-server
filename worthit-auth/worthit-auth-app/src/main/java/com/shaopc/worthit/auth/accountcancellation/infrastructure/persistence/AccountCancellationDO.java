package com.shaopc.worthit.auth.accountcancellation.infrastructure.persistence;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** auth_account_cancellation 持久化对象。 */
@Getter
@Setter
public class AccountCancellationDO {
    private Long id;
    private Long userId;
    private LocalDateTime applyAt;
    private LocalDateTime effectiveAt;
    private LocalDateTime completedAt;
    private String status;
    private LocalDateTime revokedAt;
    private Long version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
