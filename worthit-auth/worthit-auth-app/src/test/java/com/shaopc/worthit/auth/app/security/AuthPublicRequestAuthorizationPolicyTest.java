package com.shaopc.worthit.auth.app.security;

import com.shaopc.worthit.common.webmvc.security.PublicRequestAuthorizationPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthPublicRequestAuthorizationPolicyTest {

    private final PublicRequestAuthorizationPolicy policy =
            new AuthPublicRequestAuthorizationPolicy();

    @Test
    void onlySupportedLoginPathsAreAnonymous() {
        assertThat(policy.requiresLogin("/api/v1/auth/wechat/login")).isFalse();
        assertThat(policy.requiresLogin("/api/v1/auth/password/login"))
                .isFalse();
        assertThat(policy.requiresLogin("/api/v1/auth/wechat/login/extra"))
                .isTrue();
        assertThat(policy.requiresLogin("/api/v1/auth/data-export")).isTrue();
        assertThat(policy.requiresLogin("/api/v1/auth/profile")).isTrue();
    }
}
