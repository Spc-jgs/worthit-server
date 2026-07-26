package com.shaopc.worthit.tracking;

import com.shaopc.worthit.common.core.trace.TraceIdGenerator;
import com.shaopc.worthit.common.http.trace.TraceIdProvider;
import com.shaopc.worthit.tracking.infrastructure.client.ServletTraceIdProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * WorthIt 物品追踪服务启动入口。
 */
@SpringBootApplication
public class WorthItTrackingApplication {

    /**
     * 启动物品追踪服务。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WorthItTrackingApplication.class, args);
    }

    /**
     * 传播经可信来源过滤器确认的 TraceId。
     *
     * @param traceIdGenerator 无请求上下文时的 TraceId 生成器
     * @return 当前调用链 TraceId 提供器
     */
    @Bean
    TraceIdProvider traceIdProvider(TraceIdGenerator traceIdGenerator) {
        return new ServletTraceIdProvider(traceIdGenerator);
    }
}
