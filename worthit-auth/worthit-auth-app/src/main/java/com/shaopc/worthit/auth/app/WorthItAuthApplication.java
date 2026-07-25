package com.shaopc.worthit.auth.app;

import com.shaopc.worthit.common.core.trace.TraceIdGenerator;
import com.shaopc.worthit.common.core.trace.UuidTraceIdGenerator;
import com.shaopc.worthit.common.data.config.WorthItMybatisPlusConfiguration;
import com.shaopc.worthit.common.security.sametoken.SaTokenSameTokenService;
import com.shaopc.worthit.common.security.sametoken.SameTokenVerifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * WorthIt 认证服务启动入口。
 */
@Import(WorthItMybatisPlusConfiguration.class)
@SpringBootApplication
public class WorthItAuthApplication {

    /**
     * 启动认证服务。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WorthItAuthApplication.class, args);
    }

    /**
     * 提供内部请求 Same-Token 校验器。
     *
     * @return Sa-Token Same-Token 适配器
     */
    @Bean
    SameTokenVerifier sameTokenVerifier() {
        return new SaTokenSameTokenService();
    }

    /**
     * 提供安全失败场景使用的 TraceId 生成器。
     *
     * @return UUID TraceId 生成器
     */
    @Bean
    TraceIdGenerator traceIdGenerator() {
        return new UuidTraceIdGenerator();
    }
}
