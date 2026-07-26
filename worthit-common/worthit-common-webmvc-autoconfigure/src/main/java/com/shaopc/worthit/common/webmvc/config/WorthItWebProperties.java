package com.shaopc.worthit.common.webmvc.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * WorthIt Servlet Web 公共能力配置。
 */
@Getter
@ConfigurationProperties("worthit.web")
public final class WorthItWebProperties {

    /**
     * TraceId 运行时配置。
     */
    private final Trace trace = new Trace();

    /**
     * 统一异常处理配置。
     */
    private final ErrorHandling errorHandling = new ErrorHandling();

    /**
     * OpenAPI 分组配置。
     */
    private final Openapi openapi = new Openapi();

    /**
     * TraceId 能力配置。
     */
    @Getter
    @Setter
    public static final class Trace {

        /**
         * 是否启用 TraceId 能力。
         */
        private boolean enabled = true;
    }

    /**
     * 统一异常处理能力配置。
     */
    @Getter
    @Setter
    public static final class ErrorHandling {

        /**
         * 是否启用统一异常处理能力。
         */
        private boolean enabled = true;
    }

    /**
     * OpenAPI 分组能力配置。
     */
    @Getter
    @Setter
    public static final class Openapi {

        /**
         * 是否启用 OpenAPI 分组能力。
         */
        private boolean enabled;
    }
}
