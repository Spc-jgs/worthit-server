package com.shaopc.worthit.common.webmvc.error;

import com.shaopc.worthit.common.core.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 将稳定业务错误码解析为 HTTP 传输状态。
 */
@FunctionalInterface
public interface ErrorHttpStatusResolver {

    /**
     * 解析错误码对应的 HTTP 状态。
     *
     * @param errorCode 稳定错误码
     * @return HTTP 状态
     */
    HttpStatus resolve(ErrorCode errorCode);
}
