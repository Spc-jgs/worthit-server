package com.shaopc.worthit.auth;

import com.shaopc.worthit.auth.dataexport.application.DataExportProperties;
import com.shaopc.worthit.auth.accountcancellation.infrastructure.scheduler.AccountCancellationProperties;
import com.shaopc.worthit.auth.dataexport.infrastructure.client.AuthServletTraceIdProvider;
import com.shaopc.worthit.common.core.trace.TraceIdGenerator;
import com.shaopc.worthit.common.http.trace.TraceIdProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * WorthIt 认证服务启动入口。
 */
@SpringBootApplication
@EnableConfigurationProperties({
        DataExportProperties.class,
        AccountCancellationProperties.class
})
public class WorthItAuthApplication {

    /**
     * 启动认证服务。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WorthItAuthApplication.class, args);
    }

    /** 为 Auth 的内部服务调用传播当前请求 TraceId。 */
    @Bean
    TraceIdProvider traceIdProvider(TraceIdGenerator traceIdGenerator) {
        return new AuthServletTraceIdProvider(traceIdGenerator);
    }
}
