package com.shaopc.worthit.auth.authentication.infrastructure.persistence;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Auth 持久化所需的时间基础配置。
 */
@Configuration(proxyBeanMethods = false)
public class AuthPersistenceConfiguration {

    /** WorthIt 业务日期与数据库时间使用的时区。 */
    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of("Asia/Shanghai");

    /**
     * 提供可在测试中覆盖的业务时钟。
     *
     * @return 上海时区系统时钟
     */
    @Bean
    @ConditionalOnMissingBean
    Clock authClock() {
        return Clock.system(BUSINESS_ZONE);
    }
}
