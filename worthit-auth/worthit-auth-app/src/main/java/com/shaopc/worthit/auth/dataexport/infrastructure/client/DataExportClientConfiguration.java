package com.shaopc.worthit.auth.dataexport.infrastructure.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.common.http.client.HttpServiceClientFactory;
import com.shaopc.worthit.common.http.config.HttpClientTimeouts;
import com.shaopc.worthit.common.http.context.InternalRequestContext;
import com.shaopc.worthit.common.http.trace.TraceIdProvider;
import com.shaopc.worthit.common.security.sametoken.SameTokenProvider;
import com.shaopc.worthit.reminder.client.api.ReminderDataExportClient;
import com.shaopc.worthit.reminder.client.api.ReminderAccountCancellationClient;
import com.shaopc.worthit.tracking.client.api.TrackingDataExportClient;
import com.shaopc.worthit.tracking.client.api.TrackingAccountCancellationClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.net.URI;

/** 装配 Auth 到两个数据所有方的阻塞式 HTTP Interface Client。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        TrackingDataExportClientProperties.class,
        ReminderDataExportClientProperties.class
})
public class DataExportClientConfiguration {

    private static final String CALLER_SERVICE = "worthit-auth";

    /** 提供 Spring Cloud LoadBalancer 增强的共享 Builder。 */
    @Bean
    @LoadBalanced
    RestClient.Builder dataExportRestClientBuilder() {
        return RestClient.builder();
    }

    /** 提供公共 HTTP Interface 工厂。 */
    @Bean
    HttpServiceClientFactory authHttpServiceClientFactory(
            ObjectMapper objectMapper) {
        return new HttpServiceClientFactory(objectMapper);
    }

    /** 创建 Tracking 数据导出 Client。 */
    @Bean
    TrackingDataExportClient trackingDataExportClient(
            HttpServiceClientFactory factory,
            @LoadBalanced RestClient.Builder builder,
            TrackingDataExportClientProperties properties,
            SameTokenProvider sameTokenProvider,
            TraceIdProvider traceIdProvider) {
        return factory.create(
                TrackingDataExportClient.class,
                "worthit-tracking",
                URI.create("http://" + properties.serviceId()),
                builder,
                new HttpClientTimeouts(
                        properties.connectTimeout(), properties.readTimeout()),
                new InternalRequestContext(
                        CALLER_SERVICE, sameTokenProvider, traceIdProvider));
    }

    /** 创建 Reminder 数据导出 Client。 */
    @Bean
    ReminderDataExportClient reminderDataExportClient(
            HttpServiceClientFactory factory,
            @LoadBalanced RestClient.Builder builder,
            ReminderDataExportClientProperties properties,
            SameTokenProvider sameTokenProvider,
            TraceIdProvider traceIdProvider) {
        return factory.create(
                ReminderDataExportClient.class,
                "worthit-reminder",
                URI.create("http://" + properties.serviceId()),
                builder,
                new HttpClientTimeouts(
                        properties.connectTimeout(), properties.readTimeout()),
                new InternalRequestContext(
                        CALLER_SERVICE, sameTokenProvider, traceIdProvider));
    }

    /** 创建 Tracking 账号注销清理 Client。 */
    @Bean
    TrackingAccountCancellationClient trackingAccountCancellationClient(
            HttpServiceClientFactory factory,
            @LoadBalanced RestClient.Builder builder,
            TrackingDataExportClientProperties properties,
            SameTokenProvider sameTokenProvider,
            TraceIdProvider traceIdProvider) {
        return factory.create(
                TrackingAccountCancellationClient.class,
                "worthit-tracking",
                URI.create("http://" + properties.serviceId()),
                builder,
                new HttpClientTimeouts(
                        properties.connectTimeout(), properties.readTimeout()),
                new InternalRequestContext(
                        CALLER_SERVICE, sameTokenProvider, traceIdProvider));
    }

    /** 创建 Reminder 账号注销清理 Client。 */
    @Bean
    ReminderAccountCancellationClient reminderAccountCancellationClient(
            HttpServiceClientFactory factory,
            @LoadBalanced RestClient.Builder builder,
            ReminderDataExportClientProperties properties,
            SameTokenProvider sameTokenProvider,
            TraceIdProvider traceIdProvider) {
        return factory.create(
                ReminderAccountCancellationClient.class,
                "worthit-reminder",
                URI.create("http://" + properties.serviceId()),
                builder,
                new HttpClientTimeouts(
                        properties.connectTimeout(), properties.readTimeout()),
                new InternalRequestContext(
                        CALLER_SERVICE, sameTokenProvider, traceIdProvider));
    }
}
