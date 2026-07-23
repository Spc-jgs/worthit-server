package com.shaopc.worthit.common.core.error;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusinessExceptionTest {

    @Test
    void shouldUseStableCodeAndDefaultMessage() {
        BusinessException exception = new BusinessException(TestErrorCode.INVALID);

        assertThat(exception.errorCode()).isSameAs(TestErrorCode.INVALID);
        assertThat(exception.code()).isEqualTo("VAL_INVALID");
        assertThat(exception.getMessage()).isEqualTo("参数无效");
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void shouldKeepCustomMessageAndCause() {
        IllegalStateException cause = new IllegalStateException("根因");

        BusinessException exception =
                new BusinessException(TestErrorCode.INVALID, "自定义消息", cause);

        assertThat(exception.errorCode()).isSameAs(TestErrorCode.INVALID);
        assertThat(exception.code()).isEqualTo("VAL_INVALID");
        assertThat(exception.getMessage()).isEqualTo("自定义消息");
        assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    void shouldRejectMissingOrInvalidErrorCode() {
        assertThatThrownBy(() -> new BusinessException(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("错误码不能为空");

        assertThatThrownBy(() -> new BusinessException(new StubErrorCode(" ", "参数无效")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("错误码编码不能为空");

        assertThatThrownBy(() -> new BusinessException(new StubErrorCode("VAL_INVALID", " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("异常消息不能为空");
    }

    @Test
    void shouldRejectBlankCustomMessage() {
        assertThatThrownBy(() -> new BusinessException(TestErrorCode.INVALID, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("异常消息不能为空");
    }

    /**
     * 测试使用的稳定错误码。
     */
    private enum TestErrorCode implements ErrorCode {
        /**
         * 表示参数校验失败。
         */
        INVALID;

        @Override
        public String code() {
            return "VAL_INVALID";
        }

        @Override
        public String defaultMessage() {
            return "参数无效";
        }
    }

    private record StubErrorCode(String code, String defaultMessage) implements ErrorCode {
    }
}
