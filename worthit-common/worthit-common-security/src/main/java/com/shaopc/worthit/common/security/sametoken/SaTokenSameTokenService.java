package com.shaopc.worthit.common.security.sametoken;

import cn.dev33.satoken.same.SaSameUtil;

/**
 * 基于 Sa-Token 全局 Same-Token 能力的默认适配器。
 */
public final class SaTokenSameTokenService implements SameTokenProvider, SameTokenVerifier {

    @Override
    public String currentToken() {
        return SaSameUtil.getToken();
    }

    @Override
    public void verify(String token) {
        SaSameUtil.checkToken(token);
    }
}
