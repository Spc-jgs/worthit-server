package com.shaopc.worthit.common.http.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiResponseErrorHandlerTest {

    private final ApiResponseErrorHandler handler =
            new ApiResponseErrorHandler(new ObjectMapper(), "worthit-reminder");
    private final MockClientHttpRequest request =
            new MockClientHttpRequest(HttpMethod.POST, URI.create("http://worthit-reminder"));

    @Test
    void decodesBoundedUnifiedErrorEnvelope() {
        String body = """
                {
                  "success": false,
                  "code": "IDEM_CONFLICT",
                  "message": "幂等键与请求内容冲突",
                  "data": null,
                  "traceId": "trace-remote"
                }
                """;

        assertThatThrownBy(() -> handler.handle(request, response(body, HttpStatus.CONFLICT)))
                .isInstanceOfSatisfying(RemoteServiceException.class, exception -> {
                    assertThat(exception.targetService()).isEqualTo("worthit-reminder");
                    assertThat(exception.statusCode()).isEqualTo(409);
                    assertThat(exception.remoteCode()).isEqualTo("IDEM_CONFLICT");
                    assertThat(exception.remoteTraceId()).isEqualTo("trace-remote");
                    assertThat(exception.getMessage()).contains("幂等键与请求内容冲突");
                });
    }

    @Test
    void doesNotExposeHtmlEmptyOrOversizedResponseBodies() {
        String oversizedSecret = "secret-remote-body-" + "x".repeat(65_536);

        assertSafeFallback("<html>secret-html</html>", "secret-html");
        assertSafeFallback("", "secret");
        assertSafeFallback(oversizedSecret, "secret-remote-body");
    }

    private void assertSafeFallback(String body, String forbiddenText) {
        assertThatThrownBy(() -> handler.handle(
                request, response(body, HttpStatus.INTERNAL_SERVER_ERROR)))
                .isInstanceOfSatisfying(RemoteServiceException.class, exception -> {
                    assertThat(exception.remoteCode()).isEqualTo("REMOTE_HTTP_ERROR");
                    assertThat(exception.remoteTraceId()).isNull();
                    assertThat(exception.getMessage())
                            .contains("远端服务请求失败")
                            .doesNotContain(forbiddenText);
                });
    }

    private static MockClientHttpResponse response(String body, HttpStatus status) {
        return new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8), status);
    }
}
