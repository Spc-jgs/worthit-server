package com.shaopc.worthit.tracking.infrastructure.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.common.http.error.RemoteServiceException;
import com.shaopc.worthit.common.http.trace.TraceIdProvider;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.common.security.sametoken.SameTokenProvider;
import com.shaopc.worthit.reminder.client.api.ReminderCommandClient;
import com.shaopc.worthit.reminder.client.command.ReconcileReminderCommand;
import com.shaopc.worthit.reminder.client.contract.ReminderClientContract;
import com.shaopc.worthit.reminder.client.model.ReconcileResultCode;
import com.shaopc.worthit.reminder.client.model.ReminderBusinessType;
import com.shaopc.worthit.reminder.client.model.ReminderOperationType;
import com.shaopc.worthit.reminder.client.model.ReminderType;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReminderClientConfigurationTest {

    private static final Set<String> FORBIDDEN_FIELDS = Set.of(
            "cause",
            "resolutionCause",
            "reconcileCause",
            "correction",
            "displayName");

    private final ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();
    private final AtomicReference<CapturedRequest> capturedRequest =
            new AtomicReference<>();
    private HttpServer server;
    private ApplicationContextRunner contextRunner;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                ReminderClientContract.BASE_PATH
                        + ReminderClientContract.RECONCILE_PATH,
                this::handleReconcile);
        server.start();

        contextRunner = new ApplicationContextRunner()
                .withUserConfiguration(ReminderClientConfiguration.class)
                .withBean(ObjectMapper.class, () -> objectMapper)
                .withBean(SameTokenProvider.class, () -> () -> "same-token-test")
                .withBean(TraceIdProvider.class, () -> () -> "trace-test")
                .withPropertyValues(
                        "worthit.clients.reminder.service-id=127.0.0.1:"
                                + server.getAddress().getPort(),
                        "worthit.clients.reminder.connect-timeout=1s",
                        "worthit.clients.reminder.read-timeout=100ms");
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void createsClientAndSendsFrozenReconcileContract() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ReminderCommandClient.class);

            ReminderCommandClient client =
                    context.getBean(ReminderCommandClient.class);
            var response = client.reconcile("event-001", command());

            assertThat(response.applied()).isTrue();
            assertThat(response.resultCode()).isEqualTo(ReconcileResultCode.APPLIED);
            assertThat(response.bindingId()).isEqualTo(41L);
            assertThat(capturedRequest.get()).satisfies(captured -> {
                assertThat(captured.path()).isEqualTo(
                        "/internal/v1/reminders/reconcile");
                assertThat(captured.idempotencyKey()).isEqualTo("event-001");
                assertThat(captured.sameToken()).isEqualTo("same-token-test");
                assertThat(captured.callerService()).isEqualTo("worthit-tracking");
                assertThat(captured.traceId()).isEqualTo("trace-test");
                assertThat(captured.body().path("operationType").textValue())
                        .isEqualTo("INITIAL_SYNC");
                assertThat(captured.body().path("schemaVersion").intValue())
                        .isEqualTo(1);
                assertThat(FORBIDDEN_FIELDS)
                        .noneMatch(captured.body()::has);
            });
        });
    }

    @Test
    void decodesRemoteErrorAndEnforcesReadTimeout() {
        contextRunner.run(context -> {
            ReminderCommandClient client =
                    context.getBean(ReminderCommandClient.class);

            assertThatThrownBy(() -> client.reconcile("event-conflict", command()))
                    .isInstanceOfSatisfying(
                            RemoteServiceException.class,
                            exception -> {
                                assertThat(exception.targetService())
                                        .isEqualTo("worthit-reminder");
                                assertThat(exception.statusCode()).isEqualTo(409);
                                assertThat(exception.remoteCode())
                                        .isEqualTo("BIZ_CONTRACT_CONFLICT");
                                assertThat(exception.remoteTraceId())
                                        .isEqualTo("trace-reminder");
                            });
            assertThatThrownBy(() -> client.reconcile("event-slow", command()))
                    .isInstanceOf(ResourceAccessException.class)
                    .hasCauseInstanceOf(HttpTimeoutException.class);
        });
    }

    private void handleReconcile(HttpExchange exchange) throws IOException {
        String eventId = exchange.getRequestHeaders()
                .getFirst(ReminderClientContract.IDEMPOTENCY_HEADER);
        if ("event-conflict".equals(eventId)) {
            respond(
                    exchange,
                    409,
                    """
                            {
                              "success": false,
                              "code": "BIZ_CONTRACT_CONFLICT",
                              "message": "契约冲突",
                              "data": null,
                              "traceId": "trace-reminder"
                            }
                            """);
            return;
        }
        if ("event-slow".equals(eventId)) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, successBody());
            return;
        }

        capturedRequest.set(new CapturedRequest(
                exchange.getRequestURI().getPath(),
                eventId,
                exchange.getRequestHeaders()
                        .getFirst(SecurityHeaderNames.SAME_TOKEN),
                exchange.getRequestHeaders()
                        .getFirst(SecurityHeaderNames.CALLER_SERVICE),
                exchange.getRequestHeaders()
                        .getFirst(SecurityHeaderNames.TRACE_ID),
                objectMapper.readTree(exchange.getRequestBody())));
        respond(exchange, 200, successBody());
    }

    private static void respond(HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set(
                "Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try {
            exchange.getResponseBody().write(bytes);
        } finally {
            exchange.close();
        }
    }

    private static String successBody() {
        return """
                {
                  "applied": true,
                  "resultCode": "APPLIED",
                  "idempotent": false,
                  "bindingId": 41,
                  "lastSourceVersion": 3
                }
                """;
    }

    private static ReconcileReminderCommand command() {
        return new ReconcileReminderCommand(
                11L,
                ReminderBusinessType.ITEM,
                21L,
                ReminderType.WARRANTY,
                3L,
                LocalDate.of(2026, 8, 1),
                LocalDateTime.of(2026, 7, 30, 9, 0),
                true,
                "ACTIVE",
                ReminderOperationType.INITIAL_SYNC,
                ReminderClientContract.SCHEMA_VERSION);
    }

    private record CapturedRequest(
            String path,
            String idempotencyKey,
            String sameToken,
            String callerService,
            String traceId,
            JsonNode body) {
    }
}
