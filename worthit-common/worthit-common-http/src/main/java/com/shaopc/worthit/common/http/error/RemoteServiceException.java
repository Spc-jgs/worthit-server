package com.shaopc.worthit.common.http.error;

import java.util.Objects;

/**
 * 表示远端服务返回的可识别 HTTP 错误。
 */
public final class RemoteServiceException extends RuntimeException {

    private final String targetService;
    private final int statusCode;
    private final String remoteCode;
    private final String remoteTraceId;

    /**
     * 创建远端服务异常。
     *
     * @param targetService 目标服务名称
     * @param statusCode    HTTP 状态码
     * @param remoteCode    远端稳定错误码
     * @param remoteTraceId 远端 TraceId，可为空
     * @param safeMessage   可安全记录和展示的错误消息
     */
    public RemoteServiceException(
            String targetService,
            int statusCode,
            String remoteCode,
            String remoteTraceId,
            String safeMessage) {
        super(requireText(safeMessage, "远端错误消息"));
        this.targetService = requireText(targetService, "目标服务名称");
        this.statusCode = statusCode;
        this.remoteCode = requireText(remoteCode, "远端错误码");
        this.remoteTraceId = remoteTraceId;
    }

    /**
     * 获取目标服务名称。
     *
     * @return 目标服务名称
     */
    public String targetService() {
        return targetService;
    }

    /**
     * 获取远端 HTTP 状态码。
     *
     * @return HTTP 状态码
     */
    public int statusCode() {
        return statusCode;
    }

    /**
     * 获取远端稳定错误码。
     *
     * @return 远端错误码
     */
    public String remoteCode() {
        return remoteCode;
    }

    /**
     * 获取远端 TraceId。
     *
     * @return TraceId；响应未提供时为空
     */
    public String remoteTraceId() {
        return remoteTraceId;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + "不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + "不能为空");
        }
        return value;
    }
}
