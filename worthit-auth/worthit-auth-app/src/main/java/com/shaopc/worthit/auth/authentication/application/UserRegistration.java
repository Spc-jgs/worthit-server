package com.shaopc.worthit.auth.authentication.application;

import com.shaopc.worthit.auth.authentication.domain.AuthUser;

/**
 * 微信身份查找或首次建档结果。
 *
 * @param user    内部用户
 * @param newUser 是否为首次建档
 */
record UserRegistration(AuthUser user, boolean newUser) {
}
