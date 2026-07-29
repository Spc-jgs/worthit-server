package com.shaopc.worthit.common.http.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.common.http.config.HttpClientTimeouts;
import com.shaopc.worthit.common.http.context.InternalRequestContext;
import com.shaopc.worthit.common.http.error.RemoteServiceException;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpServiceClientFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<CapturedRequest> capturedRequest =
            new AtomicReference<>();
    private HttpServer server;
    private TestClient client;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/commands", this::handleCommand);
        server.createContext("/conflict", exchange -> respond(
                exchange,
                409,
                """
                        {
                          "success": false,
                          "code": "IDEM_CONFLICT",
                          "message": "幂等键与请求内容冲突",
                          "data": null,
                          "traceId": "trace-remote"
                        }
                        """));
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(500);
                respond(exchange, 200, "{\"value\":\"late\"}");
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                exchange.close();
            }
        });
        server.start();

        client = createClient(Duration.ofSeconds(2));
    }

    private TestClient createClient(Duration readTimeout) {
        URI baseUrl = URI.create(
                "http://127.0.0.1:" + server.getAddress().getPort());
        InternalRequestContext requestContext = new InternalRequestContext(
                "worthit-tracking",
                () -> "same-token-test",
                () -> "trace-test");
        return new HttpServiceClientFactory(objectMapper).create(
                TestClient.class,
                "worthit-reminder",
                baseUrl,
                RestClient.builder(),
                new HttpClientTimeouts(
                        Duration.ofSeconds(1), readTimeout),
                requestContext);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void sendsJsonAndTrustedHeadersAndDecodesSuccess() {
        TestResponse response = client.create(new TestRequest("payload"));

        assertThat(response.value()).isEqualTo("accepted");
        assertThat(capturedRequest.get())
                .satisfies(captured -> {
                    assertThat(captured.path()).isEqualTo("/commands");
                    assertThat(captured.body()).contains("\"value\":\"payload\"");
                    assertThat(captured.sameToken()).isEqualTo("same-token-test");
                    assertThat(captured.callerService()).isEqualTo("worthit-tracking");
                    assertThat(captured.traceId()).isEqualTo("trace-test");
                });
    }

    @Test
    void translatesErrorEnvelopeUsingTargetService() {
        assertThatThrownBy(client::conflict)
                .isInstanceOfSatisfying(RemoteServiceException.class, exception -> {
                    assertThat(exception.targetService()).isEqualTo("worthit-reminder");
                    assertThat(exception.statusCode()).isEqualTo(409);
                    assertThat(exception.remoteCode()).isEqualTo("IDEM_CONFLICT");
                    assertThat(exception.remoteTraceId()).isEqualTo("trace-remote");
                });
    }

    @Test
    void enforcesReadTimeoutAndRetainsTransportCause() {
        TestClient shortTimeoutClient = createClient(Duration.ofMillis(100));

        assertThatThrownBy(shortTimeoutClient::slow)
                .isInstanceOf(ResourceAccessException.class)
                .hasCauseInstanceOf(HttpTimeoutException.class);
    }

    private void handleCommand(HttpExchange exchange) throws IOException {
        capturedRequest.set(new CapturedRequest(
                exchange.getRequestURI().getPath(),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                exchange.getRequestHeaders().getFirst(SecurityHeaderNames.SAME_TOKEN),
                exchange.getRequestHeaders().getFirst(SecurityHeaderNames.CALLER_SERVICE),
                exchange.getRequestHeaders().getFirst(SecurityHeaderNames.TRACE_ID)));
        respond(exchange, 200, "{\"value\":\"accepted\"}");
    }

    private static void respond(HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    interface TestClient {

        @PostExchange("/commands")
        TestResponse create(@RequestBody TestRequest request);

        @GetExchange("/conflict")
        TestResponse conflict();

        @GetExchange("/slow")
        TestResponse slow();
    }

    record TestRequest(String value) {
    }

    record TestResponse(String value) {
    }

    record CapturedRequest(
            String path,
            String body,
            String sameToken,
            String callerService,
            String traceId) {
    }
}
