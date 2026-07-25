package com.shaopc.worthit.common.security.header;

import cn.dev33.satoken.same.SaSameUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityHeaderNamesTest {

    @Test
    void exposesFrozenTrustedHeaderNames() {
        assertThat(SecurityHeaderNames.AUTHORIZATION).isEqualTo("Authorization");
        assertThat(SecurityHeaderNames.SAME_TOKEN).isEqualTo(SaSameUtil.SAME_TOKEN);
        assertThat(SecurityHeaderNames.CALLER_SERVICE).isEqualTo("X-Caller-Service");
        assertThat(SecurityHeaderNames.USER_ID).isEqualTo("X-User-Id");
        assertThat(SecurityHeaderNames.SESSION_ID).isEqualTo("X-Session-Id");
        assertThat(SecurityHeaderNames.TRACE_ID).isEqualTo("X-Trace-Id");
    }
}
