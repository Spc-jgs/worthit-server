package com.shaopc.worthit.auth.app.security.sametoken;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 仅在 Auth 明确启用时装配 Same-Token 单点轮换能力。
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(SameTokenRotationProperties.class)
@ConditionalOnProperty(
        prefix = "worthit.security.same-token.rotation",
        name = "enabled",
        havingValue = "true")
public class SameTokenRotationConfiguration {

    /**
     * 提供 Sa-Token 轮换适配器。
     *
     * @return 轮换适配器
     */
    @Bean
    SameTokenRotationGateway sameTokenRotationGateway() {
        return new SaTokenSameTokenRotationGateway();
    }

    /**
     * 提供带 owner 安全释放语义的 Redis 锁。
     *
     * @param redis Redis 客户端
     * @return Redis 主节点锁
     */
    @Bean
    RedisLeaderLock sameTokenRedisLeaderLock(StringRedisTemplate redis) {
        return new RedisTemplateLeaderLock(redis);
    }

    /**
     * 提供 Auth 独占的 Same-Token 轮换调度器。
     *
     * @param gateway 轮换适配器
     * @param leaderLock Redis 主节点锁
     * @param properties 轮换参数
     * @param meterRegistry 指标注册器
     * @return 轮换调度器
     */
    @Bean
    SameTokenRotationScheduler sameTokenRotationScheduler(
            SameTokenRotationGateway gateway,
            RedisLeaderLock leaderLock,
            SameTokenRotationProperties properties,
            MeterRegistry meterRegistry) {
        return new SameTokenRotationScheduler(
                gateway, leaderLock, properties, meterRegistry);
    }
}
