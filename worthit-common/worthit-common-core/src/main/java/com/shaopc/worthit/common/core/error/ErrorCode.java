package com.shaopc.worthit.common.core.error;

/**
 * 定义跨层传递的稳定错误码契约。
 *
 * <p>错误码用于机器判断，默认消息用于未提供定制消息时的人类可读提示。</p>
 */
public interface ErrorCode {

    /**
     * 获取稳定的机器错误码。
     *
     * @return 非空的错误码编码
     */
    String code();

    /**
     * 获取错误码对应的默认中文消息。
     *
     * @return 非空的默认消息
     */
    String defaultMessage();
}
