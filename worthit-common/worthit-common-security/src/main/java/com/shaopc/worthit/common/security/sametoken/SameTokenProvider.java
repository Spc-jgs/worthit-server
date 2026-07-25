package com.shaopc.worthit.common.security.sametoken;

/**
 * 提供当前内部服务调用使用的 Same-Token。
 */
@FunctionalInterface
public interface SameTokenProvider {

    /**
     * 获取当前有效的 Same-Token。
     *
     * @return 当前 Same-Token
     */
    String currentToken();
}
