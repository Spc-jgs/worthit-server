package com.shaopc.worthit.common.webmvc.autoconfigure;

import com.shaopc.worthit.common.webmvc.config.WorthItWebProperties;
import com.shaopc.worthit.common.webmvc.openapi.OpenApiGroupConstants;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 为 WorthIt Servlet/MVC 应用声明 springdoc 的公网与内部文档分组。
 *
 * <p>OpenAPI 文档扫描、模型生成、JSON 端点和 Swagger UI 均由
 * springdoc-openapi 提供；本类不实现或替代 Swagger/OpenAPI 框架，只集中声明
 * WorthIt 约定的 {@code public=/api/**} 与
 * {@code internal=/internal/**} 两个 {@link GroupedOpenApi} 分组。</p>
 *
 * <p>只有应用显式启用 {@code springdoc.api-docs.enabled=true} 时才创建分组。
 * 默认及生产配置保持文档端点关闭；应用也可以通过提供同名 Bean 覆盖某个分组。</p>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(GroupedOpenApi.class)
@ConditionalOnProperty(
        prefix = "worthit.web.openapi",
        name = "enabled",
        havingValue = "true")
@ConditionalOnProperty(
        prefix = "springdoc.api-docs",
        name = "enabled",
        havingValue = "true")
@EnableConfigurationProperties(WorthItWebProperties.class)
public class WorthItOpenApiGroupsAutoConfiguration {

    /**
     * 向 springdoc 注册只包含公网接口路径的文档分组。
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
     * 向 springdoc 注册只包含内部接口路径的文档分组。
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
