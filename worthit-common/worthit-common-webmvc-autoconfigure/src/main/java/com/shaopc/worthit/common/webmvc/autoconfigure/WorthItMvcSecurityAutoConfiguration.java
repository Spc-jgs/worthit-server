package com.shaopc.worthit.common.webmvc.autoconfigure;

import cn.dev33.satoken.jwt.StpLogicJwtForSimple;
import cn.dev33.satoken.stp.StpLogic;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.common.core.trace.TraceIdGenerator;
import com.shaopc.worthit.common.security.sametoken.SaTokenSameTokenProvider;
import com.shaopc.worthit.common.security.sametoken.SaTokenSameTokenVerifier;
import com.shaopc.worthit.common.security.sametoken.SameTokenProvider;
import com.shaopc.worthit.common.security.sametoken.SameTokenVerifier;
import com.shaopc.worthit.common.webmvc.config.WorthItSecurityProperties;
import com.shaopc.worthit.common.webmvc.security.PublicAuthenticationFilter;
import com.shaopc.worthit.common.webmvc.security.PublicRequestAuthorizationPolicy;
import com.shaopc.worthit.common.webmvc.security.SaTokenUserLoginVerifier;
import com.shaopc.worthit.common.webmvc.security.ServletApiErrorWriter;
import com.shaopc.worthit.common.webmvc.security.TrustedSourceFilter;
import com.shaopc.worthit.common.webmvc.security.UserLoginVerifier;
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
 * 三个 Servlet App 共用的安全运行时自动配置。
 */
@AutoConfiguration(after = WorthItTraceAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({
        StpLogic.class,
        ObjectMapper.class,
        OncePerRequestFilter.class,
        FilterRegistrationBean.class
})
@ConditionalOnProperty(
        prefix = "worthit.security.mvc",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(WorthItSecurityProperties.class)
public class WorthItMvcSecurityAutoConfiguration {

    /**
     * 使用 JWT Simple 模式生成登录令牌。
     *
     * @return JWT Simple 登录逻辑
     */
    @Bean
    @ConditionalOnMissingBean(StpLogic.class)
    StpLogic stpLogicJwt() {
        return new StpLogicJwtForSimple();
    }

    /**
     * 提供 Same-Token 获取适配器。
     *
     * @return Sa-Token Same-Token 获取适配器
     */
    @Bean
    @ConditionalOnMissingBean(SameTokenProvider.class)
    SaTokenSameTokenProvider saTokenSameTokenProvider() {
        return new SaTokenSameTokenProvider();
    }

    /**
     * 提供 Same-Token 校验适配器。
     *
     * @return Sa-Token Same-Token 校验适配器
     */
    @Bean
    @ConditionalOnMissingBean(SameTokenVerifier.class)
    SaTokenSameTokenVerifier saTokenSameTokenVerifier() {
        return new SaTokenSameTokenVerifier();
    }

    /**
     * 提供默认用户登录态校验器。
     *
     * @return Sa-Token 用户登录态校验器
     */
    @Bean
    @ConditionalOnMissingBean
    UserLoginVerifier userLoginVerifier() {
        return new SaTokenUserLoginVerifier();
    }

    /**
     * 默认要求所有公网接口校验用户登录态。
     *
     * @return 公网请求登录策略
     */
    @Bean
    @ConditionalOnMissingBean
    PublicRequestAuthorizationPolicy publicRequestAuthorizationPolicy() {
        return path -> true;
    }

    /**
     * 提供安全过滤器统一错误写入器。
     *
     * @param objectMapper 统一响应序列化器
     * @param traceIdGenerator TraceId 生成器
     * @return Servlet API 错误写入器
     */
    @Bean
    @ConditionalOnMissingBean
    ServletApiErrorWriter servletApiErrorWriter(
            ObjectMapper objectMapper,
            TraceIdGenerator traceIdGenerator) {
        return new ServletApiErrorWriter(objectMapper, traceIdGenerator);
    }

    /**
     * 提供可信来源过滤器。
     *
     * @param sameTokenVerifier Same-Token 校验器
     * @param errorWriter 统一错误写入器
     * @return 可信来源过滤器
     */
    @Bean
    @ConditionalOnMissingBean
    TrustedSourceFilter trustedSourceFilter(
            SameTokenVerifier sameTokenVerifier,
            ServletApiErrorWriter errorWriter) {
        return new TrustedSourceFilter(sameTokenVerifier, errorWriter);
    }

    /**
     * 提供公网用户鉴权过滤器。
     *
     * @param userLoginVerifier 用户登录态校验器
     * @param authorizationPolicy 公网请求登录策略
     * @param errorWriter 统一错误写入器
     * @return 公网用户鉴权过滤器
     */
    @Bean
    @ConditionalOnMissingBean
    PublicAuthenticationFilter publicAuthenticationFilter(
            UserLoginVerifier userLoginVerifier,
            PublicRequestAuthorizationPolicy authorizationPolicy,
            ServletApiErrorWriter errorWriter) {
        return new PublicAuthenticationFilter(
                userLoginVerifier,
                authorizationPolicy,
                errorWriter);
    }

    /**
     * 显式固定可信来源过滤器顺序。
     *
     * @param filter 可信来源过滤器
     * @return Servlet 过滤器注册
     */
    @Bean
    FilterRegistrationBean<TrustedSourceFilter>
            trustedSourceFilterRegistration(TrustedSourceFilter filter) {
        FilterRegistrationBean<TrustedSourceFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    /**
     * 显式固定公网用户鉴权过滤器顺序。
     *
     * @param filter 公网用户鉴权过滤器
     * @return Servlet 过滤器注册
     */
    @Bean
    FilterRegistrationBean<PublicAuthenticationFilter>
            publicAuthenticationFilterRegistration(
                    PublicAuthenticationFilter filter) {
        FilterRegistrationBean<PublicAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 30);
        return registration;
    }
}
