package com.shaopc.worthit.common.webmvc.autoconfigure;

import com.shaopc.worthit.common.core.trace.TraceIdGenerator;
import com.shaopc.worthit.common.core.trace.UuidTraceIdGenerator;
import com.shaopc.worthit.common.webmvc.config.WorthItWebProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet 请求 TraceId 运行时自动配置。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(OncePerRequestFilter.class)
@ConditionalOnProperty(
        prefix = "worthit.web.trace",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(WorthItWebProperties.class)
public class WorthItTraceAutoConfiguration {

    /**
     * 提供默认 TraceId 生成器。
     *
     * @return UUID TraceId 生成器
     */
    @Bean
    @ConditionalOnMissingBean
    TraceIdGenerator traceIdGenerator() {
        return new UuidTraceIdGenerator();
    }
}
