package com.shaopc.worthit.common.web.fixture;

import org.springframework.web.reactive.DispatcherHandler;

public final class CommonWebDependsOnWebFluxFixture {

    public Class<DispatcherHandler> forbiddenType() {
        return DispatcherHandler.class;
    }
}
