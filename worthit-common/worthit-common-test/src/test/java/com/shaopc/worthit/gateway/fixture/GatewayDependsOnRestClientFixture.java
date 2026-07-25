package com.shaopc.worthit.gateway.fixture;

import org.springframework.web.client.RestClient;

public final class GatewayDependsOnRestClientFixture {

    private final RestClient restClient = RestClient.create();

    public RestClient restClient() {
        return restClient;
    }
}
