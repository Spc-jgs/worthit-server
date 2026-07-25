package com.shaopc.worthit.gateway.security;

import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 配置 Gateway 公网 API 登录态校验。
 */
@Configuration(proxyBeanMethods = false)
public class GatewaySaTokenConfiguration {

    /**
     * 仅拦截公网 API；登录入口不要求已有登录态。
     *
     * @param errorWriter 统一认证错误写入器
     * @return Sa-Token Reactor 过滤器
     */
    @Bean
    public SaReactorFilter saReactorFilter(
            GatewaySecurityErrorWriter errorWriter) {
        return new SaReactorFilter()
                .addInclude("/api/**")
                .addExclude("/api/v1/auth/wechat/login")
                .setAuth(ignored -> StpUtil.checkLogin())
                .setError(errorWriter::unauthorized);
    }
}
