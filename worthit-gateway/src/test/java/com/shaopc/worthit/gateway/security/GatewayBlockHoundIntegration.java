package com.shaopc.worthit.gateway.security;

import cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate;
import reactor.blockhound.BlockHound;
import reactor.blockhound.integration.BlockHoundIntegration;

/**
 * 仅放行 Sa-Token Redis 适配器已知的同步访问。
 */
public final class GatewayBlockHoundIntegration
        implements BlockHoundIntegration {

    @Override
    public void applyTo(BlockHound.Builder builder) {
        String daoClass = SaTokenDaoForRedisTemplate.class.getName();
        builder.allowBlockingCallsInside(daoClass, "get");
        builder.allowBlockingCallsInside(daoClass, "set");
        builder.allowBlockingCallsInside(daoClass, "getTimeout");
        builder.allowBlockingCallsInside(daoClass, "updateTimeout");
    }
}
