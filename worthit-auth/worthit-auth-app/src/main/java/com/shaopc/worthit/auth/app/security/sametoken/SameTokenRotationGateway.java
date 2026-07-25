package com.shaopc.worthit.auth.app.security.sametoken;

/**
 * Same-Token 轮换所需的最小 Sa-Token 能力边界。
 */
public interface SameTokenRotationGateway {

    /**
     * 获取当前 Same-Token 的剩余有效期。
     *
     * @return 剩余秒数，沿用 Sa-Token 的超时常量语义
     */
    long remainingSeconds();

    /**
     * 刷新当前 Same-Token。
     */
    void refresh();
}
