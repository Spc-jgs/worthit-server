package com.shaopc.worthit.gateway;

import com.shaopc.worthit.common.core.trace.TraceIdGenerator;
import com.shaopc.worthit.common.core.trace.UuidTraceIdGenerator;
import com.shaopc.worthit.common.security.sametoken.SaTokenSameTokenProvider;
import com.shaopc.worthit.common.security.sametoken.SameTokenProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * WorthIt API Gateway 启动入口。
 */
@SpringBootApplication
public class WorthItGatewayApplication {

    /**
     * 启动 Gateway。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WorthItGatewayApplication.class, args);
    }

    /**
     * 提供 Gateway 使用的可信 TraceId 生成器。
     *
     * @return UUID TraceId 生成器
     */
    @Bean
    TraceIdGenerator traceIdGenerator() {
        return new UuidTraceIdGenerator();
    }

    /**
     * 提供内部服务调用使用的 Same-Token。
     *
     * @return Sa-Token Same-Token 适配器
     */
    @Bean
    SameTokenProvider sameTokenProvider() {
        return new SaTokenSameTokenProvider();
    }
}
