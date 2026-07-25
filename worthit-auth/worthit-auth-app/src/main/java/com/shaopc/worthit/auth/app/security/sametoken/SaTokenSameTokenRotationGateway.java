package com.shaopc.worthit.auth.app.security.sametoken;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.same.SaSameUtil;

/**
 * 基于 Sa-Token 全局 Same-Token 模板的轮换适配器。
 */
public final class SaTokenSameTokenRotationGateway
        implements SameTokenRotationGateway {

    @Override
    public long remainingSeconds() {
        return SaManager.getSaSameTemplate().getTokenTimeout();
    }

    @Override
    public void refresh() {
        SaSameUtil.refreshToken();
    }
}
