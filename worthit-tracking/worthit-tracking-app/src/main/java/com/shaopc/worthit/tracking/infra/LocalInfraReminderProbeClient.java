package com.shaopc.worthit.tracking.infra;

import com.shaopc.worthit.common.http.client.HttpServiceClientFactory;
import com.shaopc.worthit.common.http.config.HttpClientTimeouts;
import com.shaopc.worthit.common.http.context.InternalRequestContext;
import com.shaopc.worthit.common.http.trace.TraceIdProvider;
import com.shaopc.worthit.common.security.sametoken.SameTokenProvider;
import com.shaopc.worthit.tracking.infrastructure.client.ReminderClientProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.net.URI;

/**
 * Tracking 本地联调专用的 Reminder 注册发现探针协议。
 *
 * <p>该协议与真实 Reminder 命令接口完全隔离，只验证服务发现和可信请求头。</p>
 */
@HttpExchange(url = "/internal/__infra")
public interface LocalInfraReminderProbeClient {

    /**
     * 查询 Reminder 本地联调就绪探针。
     *
     * @return Reminder 探针响应
     */
    @GetExchange(url = "/ping")
    ReminderProbeResponse ping();

    /**
     * Reminder 本地注册发现探针响应。
     *
     * @param service 服务名
     * @param probe   稳定探针状态
     */
    record ReminderProbeResponse(String service, String probe) {
    }
}

/**
 * 仅在本地基础设施联调 Profile 创建探针代理。
 */
@Profile("local-infra")
@Configuration(proxyBeanMethods = false)
class LocalInfraReminderProbeConfiguration {

    private static final String TARGET_SERVICE = "worthit-reminder";
    private static final String CALLER_SERVICE = "worthit-tracking";
    private static final URI TARGET_URI = URI.create("http://worthit-reminder");

    /**
     * 创建使用 Nacos LoadBalancer 和可信内部头的专用探针代理。
     *
     * @param factory           公共 HTTP 代理工厂
     * @param builder           负载均衡 RestClient Builder
     * @param properties        Reminder 客户端超时
     * @param sameTokenProvider 当前 Same-Token 提供器
     * @param traceIdProvider   当前 TraceId 提供器
     * @return 本地 Reminder 探针代理
     */
    @Bean
    LocalInfraReminderProbeClient localInfraReminderProbeClient(
            HttpServiceClientFactory factory,
            @LoadBalanced RestClient.Builder builder,
            ReminderClientProperties properties,
            SameTokenProvider sameTokenProvider,
            TraceIdProvider traceIdProvider) {
        HttpClientTimeouts timeouts = new HttpClientTimeouts(
                properties.connectTimeout(),
                properties.readTimeout());
        InternalRequestContext requestContext = new InternalRequestContext(
                CALLER_SERVICE,
                sameTokenProvider,
                traceIdProvider);
        return factory.create(
                LocalInfraReminderProbeClient.class,
                TARGET_SERVICE,
                TARGET_URI,
                builder,
                timeouts,
                requestContext);
    }
}
