package com.shaopc.worthit.tracking.infrastructure.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Tracking 调用 Reminder 的服务目标和传输超时配置。
 *
 * @param serviceId      Reminder 服务标识
 * @param connectTimeout 建立连接的最大等待时间
 * @param readTimeout    读取响应的最大等待时间
 */
@ConfigurationProperties("worthit.clients.reminder")
public record ReminderClientProperties(
        @DefaultValue("worthit-reminder") String serviceId,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("3s") Duration readTimeout) {

    /**
     * 校验服务标识；两个 Duration 在创建公共超时值对象时统一校验。
     */
    public ReminderClientProperties {
        if (serviceId == null || serviceId.isBlank()) {
            throw new IllegalArgumentException("Reminder服务标识不能为空");
        }
    }
}
