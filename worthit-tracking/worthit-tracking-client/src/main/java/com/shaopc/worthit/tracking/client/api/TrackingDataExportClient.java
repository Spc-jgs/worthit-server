package com.shaopc.worthit.tracking.client.api;

import com.shaopc.worthit.tracking.client.response.TrackingDataExportResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * Auth 调用 Tracking 获取当前用户导出分片的运行时中立契约。
 */
@HttpExchange(accept = "application/json")
public interface TrackingDataExportClient {

    /**
     * 获取指定内部用户的 Tracking 数据分片。
     *
     * @param userId 已由 Auth 登录态确定的正用户标识
     * @return 稳定的 Tracking 数据导出模型
     */
    @GetExchange("/internal/v1/tracking/users/{userId}/data-export")
    TrackingDataExportResponse exportUserData(
            @PathVariable("userId") long userId);
}
