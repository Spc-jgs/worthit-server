package com.shaopc.worthit.common.webmvc.autoconfigure;

import com.shaopc.worthit.common.core.trace.TraceIdGenerator;
import com.shaopc.worthit.common.core.trace.UuidTraceIdGenerator;
import com.shaopc.worthit.common.webmvc.config.WorthItWebProperties;
import com.shaopc.worthit.common.webmvc.trace.TrustedTraceIdFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet 请求 TraceId 运行时自动配置。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({OncePerRequestFilter.class, FilterRegistrationBean.class})
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

    /**
     * 为 Servlet API 请求建立可信 TraceId。
     *
     * @param traceIdGenerator TraceId 生成器
     * @return 可信 TraceId 过滤器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "worthit.web.trace",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    TrustedTraceIdFilter trustedTraceIdFilter(
            TraceIdGenerator traceIdGenerator) {
        return new TrustedTraceIdFilter(traceIdGenerator);
    }

    /**
     * 显式固定可信 TraceId 过滤器顺序。
     *
     * @param filter 可信 TraceId 过滤器
     * @return Servlet 过滤器注册
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "worthit.web.trace",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    FilterRegistrationBean<TrustedTraceIdFilter>
            trustedTraceIdFilterRegistration(TrustedTraceIdFilter filter) {
        FilterRegistrationBean<TrustedTraceIdFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }
}
