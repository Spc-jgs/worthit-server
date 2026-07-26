package com.shaopc.worthit.common.webmvc.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * WorthIt Servlet 安全运行时配置。
 */
@Getter
@ConfigurationProperties("worthit.security")
public final class WorthItSecurityProperties {

    /**
     * MVC 安全运行时配置。
     */
    private final Mvc mvc = new Mvc();

    /**
     * MVC Same-Token 与用户登录校验配置。
     */
    @Getter
    @Setter
    public static final class Mvc {

        /**
         * 是否启用 MVC Same-Token 与用户登录校验。
         */
        private boolean enabled = true;
    }
}
