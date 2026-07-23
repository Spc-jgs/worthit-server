package com.shaopc.worthit.common.webmvc.openapi;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * WorthIt OpenAPI 分组使用的稳定名称与路径。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OpenApiGroupConstants {

    /**
     * 公网 OpenAPI 分组 Bean 名称。
     */
    public static final String PUBLIC_GROUP_BEAN_NAME = "worthItPublicOpenApi";

    /**
     * 内部 OpenAPI 分组 Bean 名称。
     */
    public static final String INTERNAL_GROUP_BEAN_NAME = "worthItInternalOpenApi";

    /**
     * 公网 OpenAPI 分组名称。
     */
    public static final String PUBLIC_GROUP_NAME = "public";

    /**
     * 内部 OpenAPI 分组名称。
     */
    public static final String INTERNAL_GROUP_NAME = "internal";

    /**
     * 公网接口路径匹配规则。
     */
    public static final String PUBLIC_PATH_PATTERN = "/api/**";

    /**
     * 内部接口路径匹配规则。
     */
    public static final String INTERNAL_PATH_PATTERN = "/internal/**";
}
