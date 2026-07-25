package com.shaopc.worthit.common.security.sametoken;

/**
 * 校验内部服务调用携带的 Same-Token。
 */
@FunctionalInterface
public interface SameTokenVerifier {

    /**
     * 校验指定 Same-Token。
     *
     * @param token 待校验的 Same-Token
     */
    void verify(String token);
}
