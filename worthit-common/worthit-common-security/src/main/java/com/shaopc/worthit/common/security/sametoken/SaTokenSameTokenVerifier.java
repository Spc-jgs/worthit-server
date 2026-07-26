package com.shaopc.worthit.common.security.sametoken;

import cn.dev33.satoken.same.SaSameUtil;

/**
 * 基于 Sa-Token 校验 Same-Token。
 */
public final class SaTokenSameTokenVerifier implements SameTokenVerifier {

    @Override
    public void verify(String token) {
        SaSameUtil.checkToken(token);
    }
}
