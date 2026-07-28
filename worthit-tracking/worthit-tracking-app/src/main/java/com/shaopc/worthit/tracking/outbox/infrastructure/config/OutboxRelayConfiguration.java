package com.shaopc.worthit.tracking.outbox.infrastructure.config;

import com.shaopc.worthit.tracking.outbox.application.OutboxRelayProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启用 Outbox Relay 配置绑定和定时调度。
 */
@EnableScheduling
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OutboxRelayProperties.class)
public class OutboxRelayConfiguration {
}
