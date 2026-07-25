package com.shaopc.worthit.common.http.config;

import java.time.Duration;
import java.util.Objects;

/**
 * 定义内部 HTTP 客户端的连接和读取超时。
 *
 * @param connectTimeout 建立连接的最大等待时间
 * @param readTimeout    读取响应的最大等待时间
 */
public record HttpClientTimeouts(Duration connectTimeout, Duration readTimeout) {

    /**
     * 校验两个超时均为正数。
     */
    public HttpClientTimeouts {
        connectTimeout = requirePositive(connectTimeout, "连接超时");
        readTimeout = requirePositive(readTimeout, "读取超时");
    }

    private static Duration requirePositive(Duration value, String name) {
        Duration requiredValue = Objects.requireNonNull(value, name + "不能为空");
        if (requiredValue.isZero() || requiredValue.isNegative()) {
            throw new IllegalArgumentException(name + "必须大于0");
        }
        return requiredValue;
    }
}
