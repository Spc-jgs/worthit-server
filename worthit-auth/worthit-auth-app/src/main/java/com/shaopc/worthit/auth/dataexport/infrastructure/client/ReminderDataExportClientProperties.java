package com.shaopc.worthit.auth.dataexport.infrastructure.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** Auth 调用 Reminder 数据导出接口的目标和超时。 */
@ConfigurationProperties("worthit.clients.reminder")
public record ReminderDataExportClientProperties(
        @DefaultValue("worthit-reminder") String serviceId,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("15s") Duration readTimeout) {

    /** 校验服务标识。 */
    public ReminderDataExportClientProperties {
        if (serviceId == null || serviceId.isBlank()) {
            throw new IllegalArgumentException("Reminder服务标识不能为空");
        }
    }
}
