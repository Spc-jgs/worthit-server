package com.shaopc.worthit.reminder.client.fixture;

import org.springframework.boot.SpringApplication;

public final class ClientDependsOnBootFixture {

    public Class<SpringApplication> forbiddenType() {
        return SpringApplication.class;
    }
}
