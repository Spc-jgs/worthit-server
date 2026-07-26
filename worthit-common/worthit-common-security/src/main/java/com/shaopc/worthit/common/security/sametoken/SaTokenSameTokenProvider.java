package com.shaopc.worthit.common.security.sametoken;

import cn.dev33.satoken.same.SaSameUtil;

/**
 * 基于 Sa-Token 获取当前 Same-Token。
 */
public final class SaTokenSameTokenProvider implements SameTokenProvider {

    @Override
    public String currentToken() {
        return SaSameUtil.getToken();
    }
}
