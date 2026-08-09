package com.shaopc.worthit.auth.accountcancellation.infrastructure.scheduler;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** 基于 SET NX 与 owner 校验释放的账号注销调度锁。 */
public class RedisAccountCancellationLeaderLock
        implements AccountCancellationLeaderLock {

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] "
                            + "then return redis.call('del', KEYS[1]) "
                            + "else return 0 end",
                    Long.class);
    private final StringRedisTemplate redis;
    private final String ownerPrefix = UUID.randomUUID().toString();

    public RedisAccountCancellationLeaderLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean executeIfLeader(String lockName, Duration ttl, Runnable action) {
        String owner = ownerPrefix + ":" + UUID.randomUUID();
        if (!Boolean.TRUE.equals(
                redis.opsForValue().setIfAbsent(lockName, owner, ttl))) {
            return false;
        }
        try {
            action.run();
            return true;
        } finally {
            redis.execute(RELEASE_SCRIPT, List.of(lockName), owner);
        }
    }
}
