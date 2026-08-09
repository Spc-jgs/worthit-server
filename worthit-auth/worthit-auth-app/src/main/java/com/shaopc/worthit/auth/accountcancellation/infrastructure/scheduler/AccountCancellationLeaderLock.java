package com.shaopc.worthit.auth.accountcancellation.infrastructure.scheduler;

import java.time.Duration;

/** 账号注销调度器的 Redis 主节点锁。 */
public interface AccountCancellationLeaderLock {
    boolean executeIfLeader(String lockName, Duration ttl, Runnable action);
}
