package com.shaopc.worthit.auth.dataexport.infrastructure.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** Auth 调用 Tracking 数据导出接口的目标和超时。 */
@ConfigurationProperties("worthit.clients.tracking")
public record TrackingDataExportClientProperties(
        @DefaultValue("worthit-tracking") String serviceId,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("15s") Duration readTimeout) {

    /** 校验服务标识。 */
    public TrackingDataExportClientProperties {
        if (serviceId == null || serviceId.isBlank()) {
            throw new IllegalArgumentException("Tracking服务标识不能为空");
        }
    }
}
