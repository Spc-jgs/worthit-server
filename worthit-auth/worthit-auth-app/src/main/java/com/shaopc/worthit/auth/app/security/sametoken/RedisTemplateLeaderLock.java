package com.shaopc.worthit.auth.app.security.sametoken;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 基于 Redis SET NX 和带 owner 比较的 Lua 脚本实现主节点锁。
 */
public final class RedisTemplateLeaderLock implements RedisLeaderLock {

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] "
                            + "then return redis.call('del', KEYS[1]) "
                            + "else return 0 end",
                    Long.class);

    private final StringRedisTemplate redis;
    private final String processOwnerId = UUID.randomUUID().toString();

    /**
     * 创建 Redis 主节点锁。
     *
     * @param redis 字符串 Redis 客户端
     */
    public RedisTemplateLeaderLock(StringRedisTemplate redis) {
        this.redis = Objects.requireNonNull(redis, "Redis 客户端不能为空");
    }

    @Override
    public boolean executeIfLeader(
            String lockName, Duration ttl, Runnable action) {
        Objects.requireNonNull(lockName, "锁键不能为空");
        Objects.requireNonNull(ttl, "锁存活时间不能为空");
        Objects.requireNonNull(action, "锁内动作不能为空");

        String owner = processOwnerId + ":" + UUID.randomUUID();
        Boolean acquired = redis.opsForValue().setIfAbsent(lockName, owner, ttl);
        if (!Boolean.TRUE.equals(acquired)) {
            return false;
        }

        try {
            action.run();
            return true;
        } finally {
            releaseIfOwner(lockName, owner);
        }
    }

    /**
     * 仅当 Redis 中的 owner 与当前调用者一致时释放锁。
     *
     * @param lockName 锁键
     * @param owner 本次加锁 owner
     * @return 是否删除了锁
     */
    boolean releaseIfOwner(String lockName, String owner) {
        Long deleted = redis.execute(RELEASE_SCRIPT, List.of(lockName), owner);
        return deleted != null && deleted > 0;
    }
}
