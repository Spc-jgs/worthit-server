package com.shaopc.worthit.common.web.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.common.core.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiResponseJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesSuccessResponseWithStableFieldOrder() throws JsonProcessingException {
        ApiResponse<String> response = ApiResponse.success("payload", "trace-001");

        assertThat(objectMapper.writeValueAsString(response))
                .isEqualTo("""
                        {"success":true,"code":"OK","message":"OK","data":"payload","traceId":"trace-001"}\
                        """);
    }

    @Test
    void serializesErrorResponseWithDetailsAndNullData() throws JsonProcessingException {
        ApiResponse<Object> response = ApiResponse.error(
                TestErrorCode.INVALID_ARGUMENT,
                "trace-002",
                List.of(new FieldViolation("name", "不能为空")));

        assertThat(objectMapper.writeValueAsString(response))
                .isEqualTo("""
                        {"success":false,"code":"VAL_INVALID_ARGUMENT","message":"参数不合法","data":null,"traceId":"trace-002","details":[{"field":"name","issue":"不能为空"}]}\
                        """);
    }

    @Test
    void omitsEmptyDetails() throws JsonProcessingException {
        ApiResponse<Object> response = ApiResponse.error(
                TestErrorCode.INVALID_ARGUMENT,
                "trace-003",
                List.of());

        assertThat(objectMapper.writeValueAsString(response))
                .isEqualTo("""
                        {"success":false,"code":"VAL_INVALID_ARGUMENT","message":"参数不合法","data":null,"traceId":"trace-003"}\
                        """);
    }

    @Test
    void protectsDetailsFromExternalMutation() {
        List<FieldViolation> details = new ArrayList<>();
        details.add(new FieldViolation("name", "无效"));

        ApiResponse<Object> response =
                ApiResponse.error(TestErrorCode.INVALID_ARGUMENT, "trace-004", details);
        details.clear();

        assertThat(response.details()).containsExactly(new FieldViolation("name", "无效"));
        assertThatThrownBy(() -> response.details().add(new FieldViolation("age", "无效")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsBlankTraceId() {
        assertThatThrownBy(() -> ApiResponse.success("payload", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("链路追踪标识不能为空");
    }

    @Test
    void rejectsBlankFieldViolationValues() {
        assertThatThrownBy(() -> new FieldViolation(" ", "无效"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("字段名不能为空");
        assertThatThrownBy(() -> new FieldViolation("name", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("校验问题不能为空");
    }

    /**
     * 测试统一响应时使用的错误码。
     */
    private enum TestErrorCode implements ErrorCode {
        /**
         * 表示请求参数不合法。
         */
        INVALID_ARGUMENT;

        @Override
        public String code() {
            return "VAL_INVALID_ARGUMENT";
        }

        @Override
        public String defaultMessage() {
            return "参数不合法";
        }
    }
}
