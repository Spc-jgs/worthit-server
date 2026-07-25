package com.shaopc.worthit.auth.app.security.sametoken;

import java.time.Duration;

/**
 * 使用 Redis 互斥选出单个 Same-Token 刷新执行者。
 */
public interface RedisLeaderLock {

    /**
     * 仅在当前调用者取得指定锁时执行动作。
     *
     * @param lockName 锁键
     * @param ttl 锁存活时间
     * @param action 受保护动作
     * @return 是否取得锁并进入动作
     */
    boolean executeIfLeader(String lockName, Duration ttl, Runnable action);
}
