package com.shaopc.worthit.auth.dataexport.infrastructure.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.common.http.trace.TraceIdProvider;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.common.security.sametoken.SameTokenProvider;
import com.shaopc.worthit.reminder.client.api.ReminderDataExportClient;
import com.shaopc.worthit.tracking.client.api.TrackingDataExportClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DataExportClientConfigurationTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();
    private final List<CapturedRequest> requests = new ArrayList<>();
    private HttpServer server;
    private ApplicationContextRunner contextRunner;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/internal/v1/tracking/users/42/data-export",
                exchange -> respond(exchange, trackingBody()));
        server.createContext(
                "/internal/v1/reminders/users/42/data-export",
                exchange -> respond(exchange, reminderBody()));
        server.start();
        String target = "127.0.0.1:" + server.getAddress().getPort();
        contextRunner = new ApplicationContextRunner()
                .withUserConfiguration(DataExportClientConfiguration.class)
                .withBean(ObjectMapper.class, () -> objectMapper)
                .withBean(SameTokenProvider.class,
                        () -> () -> "same-token-test")
                .withBean(TraceIdProvider.class, () -> () -> "trace-test")
                .withPropertyValues(
                        "worthit.clients.tracking.service-id=" + target,
                        "worthit.clients.reminder.service-id=" + target);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void sendsFrozenPathAndTrustedHeadersToBothOwners() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(TrackingDataExportClient.class);
            assertThat(context).hasSingleBean(ReminderDataExportClient.class);
            assertThat(context.getBean(
                            TrackingDataExportClientProperties.class)
                    .readTimeout()).isEqualTo(java.time.Duration.ofSeconds(15));
            assertThat(context.getBean(
                            ReminderDataExportClientProperties.class)
                    .readTimeout()).isEqualTo(java.time.Duration.ofSeconds(15));

            assertThat(context.getBean(TrackingDataExportClient.class)
                    .exportUserData(42L).userId()).isEqualTo("42");
            assertThat(context.getBean(ReminderDataExportClient.class)
                    .exportUserData(42L).userId()).isEqualTo("42");
            assertThat(requests).extracting(CapturedRequest::path)
                    .containsExactly(
                            "/internal/v1/tracking/users/42/data-export",
                            "/internal/v1/reminders/users/42/data-export");
            assertThat(requests).allSatisfy(request -> {
                assertThat(request.sameToken()).isEqualTo("same-token-test");
                assertThat(request.callerService()).isEqualTo("worthit-auth");
                assertThat(request.traceId()).isEqualTo("trace-test");
            });
        });
    }

    private void respond(HttpExchange exchange, String body) throws IOException {
        requests.add(new CapturedRequest(
                exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst(
                        SecurityHeaderNames.SAME_TOKEN),
                exchange.getRequestHeaders().getFirst(
                        SecurityHeaderNames.CALLER_SERVICE),
                exchange.getRequestHeaders().getFirst(
                        SecurityHeaderNames.TRACE_ID)));
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try {
            exchange.getResponseBody().write(bytes);
        } finally {
            exchange.close();
        }
    }

    private static String trackingBody() {
        return """
                {
                  "schemaVersion":1,
                  "capturedAt":"2026-08-04T00:00:00Z",
                  "timeZone":"Asia/Shanghai",
                  "userId":"42",
                  "categories":[],"items":[],"subscriptions":[],
                  "wishes":[],"disposals":[],"replacements":[]
                }
                """;
    }

    private static String reminderBody() {
        return """
                {
                  "schemaVersion":1,
                  "capturedAt":"2026-08-04T00:00:00Z",
                  "timeZone":"Asia/Shanghai",
                  "userId":"42",
                  "bindings":[],"instances":[]
                }
                """;
    }

    private record CapturedRequest(
            String path,
            String sameToken,
            String callerService,
            String traceId) {
    }
}
