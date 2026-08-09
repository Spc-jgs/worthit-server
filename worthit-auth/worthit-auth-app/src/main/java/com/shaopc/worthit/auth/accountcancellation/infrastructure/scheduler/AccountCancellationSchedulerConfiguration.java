package com.shaopc.worthit.auth.accountcancellation.infrastructure.scheduler;

import com.shaopc.worthit.auth.accountcancellation.application.AccountCancellationExecutionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 显式启用时装配账号注销 Redis Leader 调度。 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
        prefix = "worthit.account-cancellation",
        name = "enabled",
        havingValue = "true")
public class AccountCancellationSchedulerConfiguration {

    @Bean
    AccountCancellationLeaderLock accountCancellationLeaderLock(
            StringRedisTemplate redis) {
        return new RedisAccountCancellationLeaderLock(redis);
    }

    @Bean
    AccountCancellationScheduler accountCancellationScheduler(
            AccountCancellationExecutionService service,
            AccountCancellationLeaderLock leaderLock,
            AccountCancellationProperties properties) {
        return new AccountCancellationScheduler(service, leaderLock, properties);
    }
}
