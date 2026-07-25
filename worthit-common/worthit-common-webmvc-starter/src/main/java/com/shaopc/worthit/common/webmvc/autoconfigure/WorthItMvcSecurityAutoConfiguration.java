package com.shaopc.worthit.common.webmvc.autoconfigure;

import cn.dev33.satoken.jwt.StpLogicJwtForSimple;
import cn.dev33.satoken.stp.StpLogic;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.common.core.trace.TraceIdGenerator;
import com.shaopc.worthit.common.core.trace.UuidTraceIdGenerator;
import com.shaopc.worthit.common.security.sametoken.SaTokenSameTokenService;
import com.shaopc.worthit.common.security.sametoken.SameTokenProvider;
import com.shaopc.worthit.common.security.sametoken.SameTokenVerifier;
import com.shaopc.worthit.common.webmvc.security.PublicRequestAuthorizationPolicy;
import com.shaopc.worthit.common.webmvc.security.SaTokenUserLoginVerifier;
import com.shaopc.worthit.common.webmvc.security.TrustedSourceFilter;
import com.shaopc.worthit.common.webmvc.security.UserLoginVerifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * 三个 Servlet App 共用的安全运行时自动配置。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
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
     * 提供 Same-Token 的生成与校验适配器。
     *
     * @return Sa-Token Same-Token 适配器
     */
    @Bean
    @ConditionalOnMissingBean({
            SameTokenProvider.class,
            SameTokenVerifier.class
    })
    SaTokenSameTokenService saTokenSameTokenService() {
        return new SaTokenSameTokenService();
    }

    /**
     * 提供安全失败场景使用的 TraceId 生成器。
     *
     * @return UUID TraceId 生成器
     */
    @Bean
    @ConditionalOnMissingBean
    TraceIdGenerator traceIdGenerator() {
        return new UuidTraceIdGenerator();
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
     * 提供可信来源过滤器。
     *
     * @param sameTokenVerifier Same-Token 校验器
     * @param traceIdGenerator TraceId 生成器
     * @param objectMapper 统一响应序列化器
     * @param userLoginVerifier 用户登录态校验器
     * @param authorizationPolicy 公网请求登录策略
     * @return 可信来源过滤器
     */
    @Bean
    @ConditionalOnMissingBean
    TrustedSourceFilter trustedSourceFilter(
            SameTokenVerifier sameTokenVerifier,
            TraceIdGenerator traceIdGenerator,
            ObjectMapper objectMapper,
            UserLoginVerifier userLoginVerifier,
            PublicRequestAuthorizationPolicy authorizationPolicy) {
        return new TrustedSourceFilter(
                sameTokenVerifier,
                traceIdGenerator,
                objectMapper,
                userLoginVerifier,
                authorizationPolicy);
    }
}
