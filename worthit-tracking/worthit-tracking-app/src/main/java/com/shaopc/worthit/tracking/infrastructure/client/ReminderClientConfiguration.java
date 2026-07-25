package com.shaopc.worthit.tracking.infrastructure.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.common.http.client.HttpServiceClientFactory;
import com.shaopc.worthit.common.http.config.HttpClientTimeouts;
import com.shaopc.worthit.common.http.context.InternalRequestContext;
import com.shaopc.worthit.common.http.trace.TraceIdProvider;
import com.shaopc.worthit.common.security.sametoken.SameTokenProvider;
import com.shaopc.worthit.reminder.client.api.ReminderCommandClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.net.URI;

/**
 * 装配 Tracking 到 Reminder 的阻塞式 HTTP Interface 客户端。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ReminderClientProperties.class)
public class ReminderClientConfiguration {

    private static final String TARGET_SERVICE = "worthit-reminder";
    private static final String CALLER_SERVICE = "worthit-tracking";

    /**
     * 提供可由 Spring Cloud LoadBalancer 增强的 RestClient Builder。
     *
     * @return 负载均衡 RestClient Builder
     */
    @Bean
    @LoadBalanced
    RestClient.Builder reminderRestClientBuilder() {
        return RestClient.builder();
    }

    /**
     * 提供统一内部 HTTP 代理工厂。
     *
     * @param objectMapper 统一 JSON 序列化器
     * @return HTTP 代理工厂
     */
    @Bean
    HttpServiceClientFactory httpServiceClientFactory(ObjectMapper objectMapper) {
        return new HttpServiceClientFactory(objectMapper);
    }

    /**
     * 创建 Reminder 命令客户端。
     *
     * @param factory           公共 HTTP 代理工厂
     * @param builder           负载均衡 RestClient Builder
     * @param properties        Reminder 客户端配置
     * @param sameTokenProvider 当前 Same-Token 提供器
     * @param traceIdProvider   当前 TraceId 提供器
     * @return Reminder HTTP Interface 代理
     */
    @Bean
    ReminderCommandClient reminderCommandClient(
            HttpServiceClientFactory factory,
            @LoadBalanced RestClient.Builder builder,
            ReminderClientProperties properties,
            SameTokenProvider sameTokenProvider,
            TraceIdProvider traceIdProvider) {
        HttpClientTimeouts timeouts = new HttpClientTimeouts(
                properties.connectTimeout(), properties.readTimeout());
        InternalRequestContext requestContext = new InternalRequestContext(
                CALLER_SERVICE, sameTokenProvider, traceIdProvider);
        return factory.create(
                ReminderCommandClient.class,
                TARGET_SERVICE,
                URI.create("http://" + properties.serviceId()),
                builder,
                timeouts,
                requestContext);
    }
}
