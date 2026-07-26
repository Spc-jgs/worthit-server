package com.shaopc.worthit.common.webmvc.autoconfigure;

import cn.dev33.satoken.exception.NotLoginException;
import com.shaopc.worthit.common.core.trace.TraceIdGenerator;
import com.shaopc.worthit.common.webmvc.config.WorthItWebProperties;
import com.shaopc.worthit.common.webmvc.error.DefaultErrorHttpStatusResolver;
import com.shaopc.worthit.common.webmvc.error.ErrorHttpStatusResolver;
import com.shaopc.worthit.common.webmvc.error.RemoteServiceExceptionHandler;
import com.shaopc.worthit.common.webmvc.error.WorthItRestExceptionHandler;
import jakarta.validation.ConstraintViolationException;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * WorthIt Servlet/MVC 统一异常边界自动配置。
 */
@AutoConfiguration(after = WorthItTraceAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({
        RestControllerAdvice.class,
        ConstraintViolationException.class,
        NotLoginException.class
})
@ConditionalOnProperty(
        prefix = "worthit.web.error-handling",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(WorthItWebProperties.class)
@Import(WorthItErrorHandlingAutoConfiguration
        .RemoteServiceErrorHandlingConfiguration.class)
public class WorthItErrorHandlingAutoConfiguration {

    /**
     * 提供 WorthIt 稳定错误码的默认 HTTP 状态解析。
     *
     * @return 默认 HTTP 状态解析器
     */
    @Bean
    @ConditionalOnMissingBean
    ErrorHttpStatusResolver errorHttpStatusResolver() {
        return new DefaultErrorHttpStatusResolver();
    }

    /**
     * 提供统一 MVC 异常处理器。
     *
     * @param statusResolver HTTP 状态解析器
     * @param traceIdGenerator TraceId 生成器
     * @return 统一 MVC 异常处理器
     */
    @Bean
    @ConditionalOnMissingBean
    WorthItRestExceptionHandler worthItRestExceptionHandler(
            ErrorHttpStatusResolver statusResolver,
            TraceIdGenerator traceIdGenerator) {
        return new WorthItRestExceptionHandler(
                statusResolver,
                traceIdGenerator);
    }

    /**
     * 仅在 common-http 存在时装配远程服务异常处理器。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name =
            "com.shaopc.worthit.common.http.error.RemoteServiceException")
    static class RemoteServiceErrorHandlingConfiguration {

        /**
         * 提供远程服务异常处理器。
         *
         * @param traceIdGenerator TraceId 生成器
         * @return 远程服务异常处理器
         */
        @Bean
        @ConditionalOnMissingBean
        RemoteServiceExceptionHandler remoteServiceExceptionHandler(
                TraceIdGenerator traceIdGenerator) {
            return new RemoteServiceExceptionHandler(traceIdGenerator);
        }
    }
}
