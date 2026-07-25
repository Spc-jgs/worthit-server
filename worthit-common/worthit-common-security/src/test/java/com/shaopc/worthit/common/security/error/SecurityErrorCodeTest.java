package com.shaopc.worthit.common.security.error;

import com.shaopc.worthit.common.core.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityErrorCodeTest {

    @Test
    void exposesOnlyFrozenSecurityErrors() {
        assertThat(SecurityErrorCode.values())
                .extracting(SecurityErrorCode::code)
                .containsExactly("AUTH_UNAUTHORIZED", "AUTH_FORBIDDEN");
    }

    @Test
    void implementsCommonErrorCodeContract() {
        assertThat(SecurityErrorCode.AUTH_UNAUTHORIZED)
                .isInstanceOf(ErrorCode.class);
        assertThat(SecurityErrorCode.AUTH_UNAUTHORIZED.defaultMessage())
                .isEqualTo("未登录或登录已失效");
        assertThat(SecurityErrorCode.AUTH_FORBIDDEN.defaultMessage())
                .isEqualTo("没有权限访问该资源");
    }
}
