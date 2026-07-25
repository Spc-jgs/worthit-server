package com.shaopc.worthit.gateway.security;

import cn.dev33.satoken.jwt.StpLogicJwtForSimple;
import cn.dev33.satoken.stp.StpLogic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway 的 Sa-Token 运行时配置。
 */
@Configuration(proxyBeanMethods = false)
public class SaTokenRuntimeConfiguration {

    /**
     * 使用 JWT Simple 模式生成登录令牌。
     *
     * @return JWT Simple 登录逻辑
     */
    @Bean
    StpLogic stpLogicJwt() {
        return new StpLogicJwtForSimple();
    }
}
