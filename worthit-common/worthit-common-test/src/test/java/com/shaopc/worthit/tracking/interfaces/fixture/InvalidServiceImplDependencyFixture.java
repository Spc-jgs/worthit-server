package com.shaopc.worthit.tracking.interfaces.fixture;

import com.shaopc.worthit.tracking.application.fixture.ValidExampleServiceImpl;

/**
 * 错误地绑定应用服务实现类的接口适配层夹具。
 */
public class InvalidServiceImplDependencyFixture {

    private final ValidExampleServiceImpl service;

    public InvalidServiceImplDependencyFixture(
            ValidExampleServiceImpl service) {
        this.service = service;
    }

    public void invoke() {
        service.execute();
    }
}
