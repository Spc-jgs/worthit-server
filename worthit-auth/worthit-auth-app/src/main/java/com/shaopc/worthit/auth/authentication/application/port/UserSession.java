package com.shaopc.worthit.auth.authentication.application.port;

/**
 * 用户登录会话端口。
 */
public interface UserSession {

    /**
     * 为内部用户建立登录态。
     *
     * @param userId 内部用户标识
     * @return 已签发令牌
     */
    IssuedToken login(long userId);

    /**
     * 获取当前登录用户标识。
     *
     * @return 内部用户标识
     */
    long currentUserId();

    /**
     * 注销当前登录态。
     */
    void logout();
}
