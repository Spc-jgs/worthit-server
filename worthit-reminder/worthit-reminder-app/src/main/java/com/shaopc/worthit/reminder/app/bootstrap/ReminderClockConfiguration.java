package com.shaopc.worthit.reminder.app.bootstrap;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Reminder 业务时间配置。
 */
@Configuration(proxyBeanMethods = false)
public class ReminderClockConfiguration {

    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of("Asia/Shanghai");

    /**
     * 提供可替换、可测试的上海时区业务时钟。
     */
    @Bean
    Clock reminderClock() {
        return Clock.system(BUSINESS_ZONE);
    }
}
