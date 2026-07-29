package com.shaopc.worthit.common.webmvc.autoconfigure;

import cn.dev33.satoken.util.SaTokenConsts;

/**
 * WorthIt Servlet 安全过滤链顺序。
 */
final class WorthItServletFilterOrder {

    static final int TRUSTED_SOURCE =
            SaTokenConsts.SA_TOKEN_CONTEXT_FILTER_ORDER + 10;
    static final int TRUSTED_TRACE = TRUSTED_SOURCE + 10;
    static final int PUBLIC_AUTHENTICATION = TRUSTED_TRACE + 10;

    private WorthItServletFilterOrder() {
    }
}
