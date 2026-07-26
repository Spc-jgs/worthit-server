package com.shaopc.worthit.tracking.bootstrap;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Tracking 业务时间配置。
 */
@Configuration(proxyBeanMethods = false)
public class TrackingClockConfiguration {

    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of("Asia/Shanghai");

    /**
     * 提供可测试的业务时钟。
     *
     * @return 上海时区系统时钟
     */
    @Bean
    Clock trackingClock() {
        return Clock.system(BUSINESS_ZONE);
    }
}
