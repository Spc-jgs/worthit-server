package com.shaopc.worthit.common.webmvc.autoconfigure;

import com.shaopc.worthit.common.webmvc.openapi.OpenApiGroupConstants;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * 为 WorthIt Servlet/MVC 应用装配公网与内部 OpenAPI 分组。
 *
 * <p>只有显式启用 springdoc API Docs 时才创建分组，生产环境可以通过
 * 安全默认配置保持文档端点关闭。</p>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(GroupedOpenApi.class)
@ConditionalOnProperty(
        prefix = "springdoc.api-docs",
        name = "enabled",
        havingValue = "true")
public class WorthItOpenApiAutoConfiguration {

    /**
     * 创建只包含公网接口路径的 OpenAPI 分组。
     *
     * @return 公网 OpenAPI 分组
     */
    @Bean(name = OpenApiGroupConstants.PUBLIC_GROUP_BEAN_NAME)
    @ConditionalOnMissingBean(name = OpenApiGroupConstants.PUBLIC_GROUP_BEAN_NAME)
    public GroupedOpenApi worthItPublicOpenApi() {
        return GroupedOpenApi.builder()
                .group(OpenApiGroupConstants.PUBLIC_GROUP_NAME)
                .pathsToMatch(OpenApiGroupConstants.PUBLIC_PATH_PATTERN)
                .build();
    }

    /**
     * 创建只包含内部接口路径的 OpenAPI 分组。
     *
     * @return 内部 OpenAPI 分组
     */
    @Bean(name = OpenApiGroupConstants.INTERNAL_GROUP_BEAN_NAME)
    @ConditionalOnMissingBean(name = OpenApiGroupConstants.INTERNAL_GROUP_BEAN_NAME)
    public GroupedOpenApi worthItInternalOpenApi() {
        return GroupedOpenApi.builder()
                .group(OpenApiGroupConstants.INTERNAL_GROUP_NAME)
                .pathsToMatch(OpenApiGroupConstants.INTERNAL_PATH_PATTERN)
                .build();
    }
}
