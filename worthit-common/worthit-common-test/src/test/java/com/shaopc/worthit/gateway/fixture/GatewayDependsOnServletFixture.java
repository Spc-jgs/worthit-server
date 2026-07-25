package com.shaopc.worthit.gateway.fixture;

import jakarta.servlet.Servlet;

public final class GatewayDependsOnServletFixture {

    private Servlet servlet;

    public Servlet servlet() {
        return servlet;
    }
}
