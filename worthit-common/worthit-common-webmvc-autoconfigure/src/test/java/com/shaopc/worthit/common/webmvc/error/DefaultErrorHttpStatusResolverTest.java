package com.shaopc.worthit.common.webmvc.error;

import com.shaopc.worthit.common.core.error.ErrorCode;
import com.shaopc.worthit.common.security.error.SecurityErrorCode;
import com.shaopc.worthit.common.web.error.CommonWebErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultErrorHttpStatusResolverTest {

    private final DefaultErrorHttpStatusResolver resolver =
            new DefaultErrorHttpStatusResolver();

    @Test
    void mapsCrossServiceAndSecurityCodesToTransportStatuses() {
        assertThat(resolver.resolve(CommonWebErrorCode.VAL_INVALID_ARGUMENT))
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resolver.resolve(CommonWebErrorCode.RES_NOT_FOUND))
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resolver.resolve(SecurityErrorCode.AUTH_UNAUTHORIZED))
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resolver.resolve(SecurityErrorCode.AUTH_FORBIDDEN))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resolver.resolve(CommonWebErrorCode.SYS_ERROR))
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resolver.resolve(CommonWebErrorCode.SYS_UPSTREAM))
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void defaultsDomainBusinessCodesToConflict() {
        ErrorCode domainConflict = new ErrorCode() {
            @Override
            public String code() {
                return "BIZ_CONFLICT";
            }

            @Override
            public String defaultMessage() {
                return "业务冲突";
            }
        };

        assertThat(resolver.resolve(domainConflict))
                .isEqualTo(HttpStatus.CONFLICT);
    }
}
